/*
 * Copyright 2020-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.graphql.data.analysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import graphql.ExecutionResult;
import graphql.GraphQLContext;
import graphql.analysis.QueryTraverser;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorStub;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.schema.FieldCoordinates;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.graphql.execution.SelfDescribingDataFetcher;
import org.springframework.util.ConcurrentLruCache;

public class QueryComplexityInstrumentation extends SimplePerformantInstrumentation {

	private static final Log logger = LogFactory.getLog(QueryComplexityInstrumentation.class);

	public static String COMPLEXITY_SCORE_ESTIMATE = QueryComplexityInstrumentation.class.getSimpleName() + "COMPLEXITY_SCORE_ESTIMATE";

	private static final FieldCoordinates ROOT_FIELD = FieldCoordinates.coordinates("", "");

	private final ConcurrentLruCache<ComplexityScoringContext, ComplexityScoreCalculator> calculatorCache =
			new ConcurrentLruCache<>(32, this::findCalculator);

	private final List<ComplexityScoreStrategy> complexityScoreStrategies;

	private final BiConsumer<GraphQLContext, ComplexityScore> scoreHandler;

	public QueryComplexityInstrumentation(List<ComplexityScoreStrategy> complexityScoreStrategies, BiConsumer<GraphQLContext, ComplexityScore> scoreHandler) {
		this.complexityScoreStrategies = complexityScoreStrategies;
		this.scoreHandler = scoreHandler;
	}

	public QueryComplexityInstrumentation(BiConsumer<GraphQLContext, ComplexityScore> scoreHandler) {
		this(List.of(new DefaultComplexityScoreStrategy(1, 10)), scoreHandler);
	}

	public QueryComplexityInstrumentation() {
		this(((graphQLContext, complexityScore) -> {
		}));
	}

	private ComplexityScoreCalculator findCalculator(ComplexityScoringContext context) {
		if (context.resolveDataFetcher() instanceof SelfDescribingDataFetcher<?> selfDescribingDataFetcher) {
			try {
				AnnotationAttributes annotationAttributes = AnnotatedElementUtils
						.getMergedAnnotationAttributes(selfDescribingDataFetcher.getAnnotatedElement(), SchemaComplexityStrategy.class);
				if (annotationAttributes != null) {
					Class<? extends ComplexityScoreStrategy> calculatorClass = annotationAttributes.getClass("value");
					return calculatorClass.getDeclaredConstructor().newInstance().calculator(context);
				}
			} catch (Exception exc) {
				// fallback to configured strategies
			}
		}
		for (ComplexityScoreStrategy strategy : complexityScoreStrategies) {
			ComplexityScoreCalculator calculator = strategy.calculator(context);
			if (calculator != null) {
				return calculator;
			}
		}
		throw new IllegalStateException("Could not find eligible strategy");
	}

	@Override
	public CompletableFuture<InstrumentationState> createStateAsync(InstrumentationCreateStateParameters parameters) {
		return CompletableFuture.completedFuture(new ComplexityInstrumentationState());
	}

	@Override
	public InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {

		ComplexityInstrumentationState instrumentationState = InstrumentationState.ofState(state);
		ExecutionContext executionContext = parameters.getExecutionContext();
		QueryTraverser queryTraverser = QueryTraverser.newQueryTraverser()
				.schema(executionContext.getGraphQLSchema())
				.document(executionContext.getDocument())
				.operationName(executionContext.getExecutionInput().getOperationName())
				.coercedVariables(executionContext.getCoercedVariables())
				.build();

		Map<FieldCoordinates, ComplexityScore> scoreByCoordinates = instrumentationState.scoreEstimateByCoordinates;

		queryTraverser.visitPreOrder(new QueryVisitorStub() {
			@Override
			public void visitField(QueryVisitorFieldEnvironment env) {
				// calculate score for this field
				FieldCoordinates fieldCoordinates = fromEnvironment(env);
				ComplexityScoringContext scoringContext = new ComplexityScoringContext(env);
				ComplexityScoreCalculator scoreCalculator = calculatorCache.get(scoringContext);
				ComplexityScore score = scoreCalculator.estimateScore(scoringContext);
				scoreByCoordinates.put(fieldCoordinates, score);

				// add this new field score to its parent score
				QueryVisitorFieldEnvironment parentEnvironment = env.getParentEnvironment();
				FieldCoordinates parentCoordinates = fromEnvironment(parentEnvironment);
				ComplexityScore parentScore = scoreByCoordinates.get(parentCoordinates);
				if (parentScore != null) {
					parentScore.addChild(score);
				}
			}

		});

		ComplexityScore totalScore = scoreByCoordinates.get(ROOT_FIELD);
		if (logger.isTraceEnabled()) {
			logger.trace("[" + parameters.getExecutionContext().getExecutionId() +
					"] request score estimate [" + totalScore.calculate() + "]: " + totalScore);
		}
		// set the score as a context attribute
		GraphQLContext graphQLContext = parameters.getExecutionContext().getGraphQLContext();
		graphQLContext.put(COMPLEXITY_SCORE_ESTIMATE, totalScore);

		this.scoreHandler.accept(graphQLContext, totalScore);
		return SimpleInstrumentationContext.noOp();
	}


	static FieldCoordinates fromEnvironment(QueryVisitorFieldEnvironment environment) {
		if (environment != null) {
			return FieldCoordinates.coordinates(environment.getFieldsContainer(), environment.getFieldDefinition());
		}
		return ROOT_FIELD;
	}

	static class ComplexityInstrumentationState implements InstrumentationState {

		final Map<FieldCoordinates, ComplexityScore> scoreEstimateByCoordinates;

		public ComplexityInstrumentationState() {
			this.scoreEstimateByCoordinates = new LinkedHashMap<>();
			this.scoreEstimateByCoordinates.put(ROOT_FIELD, ComplexityScore.create(ROOT_FIELD).build());
		}
	}
}
