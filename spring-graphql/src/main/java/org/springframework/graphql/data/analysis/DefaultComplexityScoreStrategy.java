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

import java.util.Collection;

import graphql.TrivialDataFetcher;
import graphql.language.InlineFragment;
import graphql.language.SelectionSetContainer;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import reactor.core.publisher.Flux;

import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.execution.SelfDescribingDataFetcher;
import org.springframework.lang.Nullable;

public class DefaultComplexityScoreStrategy implements ComplexityScoreStrategy {

	private final int defaultScore;

	private final int defaultCollectionSize;

	public DefaultComplexityScoreStrategy(int defaultScore, int defaultCollectionSize) {
		this.defaultScore = defaultScore;
		this.defaultCollectionSize = defaultCollectionSize;
	}

	@Override
	public ComplexityScoreCalculator calculator(ComplexityScoringContext context) {
		DataFetcher<?> dataFetcher = context.resolveDataFetcher();
		if (dataFetcher instanceof TrivialDataFetcher<?>) {
			return env -> ComplexityScore.createTrivial(context.getFieldCoordinates());
		}
		else if (dataFetcher instanceof SelfDescribingDataFetcher<?> selfDescribingDataFetcher) {
			return new ComplexityScoreCalculator() {
				@Override
				public ComplexityScore estimateScore(ComplexityScoringContext context) {
					return ComplexityScore.create(context.getFieldCoordinates())
							.fieldScore(DefaultComplexityScoreStrategy.this.defaultScore)
							.fragmentType(getFragmentType(context.getFieldSelectionSet()))
							.isBatched(isBatchLoader(selfDescribingDataFetcher))
							.elementCount(getElementCount(context))
							.build();
				}
			};
		}
		return null;
	}

	protected boolean isBatchLoader(SelfDescribingDataFetcher<?> dataFetcher) {
		return AnnotationUtils.findAnnotation(dataFetcher.getAnnotatedElement(), BatchMapping.class) != null;
	}

	protected int getElementCount(ComplexityScoringContext context) {
		if (isConnectionType(context.getFieldDefinition())) {
			Object first = context.getArguments().get("first");
			Object last = context.getArguments().get("last");
			if (first instanceof Integer size) {
				return size;
			}
			else if (last  instanceof Integer size) {
				return size;
			}
		}
		if (context.resolveDataFetcher() instanceof SelfDescribingDataFetcher<?> selfDescribingDataFetcher) {
			ResolvableType returnType = selfDescribingDataFetcher.getReturnType();
			if (Collection.class.isAssignableFrom(returnType.getRawClass()) || Flux.class.isAssignableFrom(returnType.getRawClass())) {
				return defaultCollectionSize;
			}
		}
		return 1;
	}

	protected boolean isConnectionType(GraphQLFieldDefinition fieldDefinition) {
		if (fieldDefinition.getType() instanceof GraphQLObjectType objectType) {
			return objectType.getName().endsWith("Connection");
		}
		return false;
	}

	@Nullable
	protected String getFragmentType(SelectionSetContainer<?> fieldSelectionSet) {
		if (fieldSelectionSet instanceof InlineFragment fragment) {
			return fragment.getTypeCondition().getName();
		}
		return null;
	}
}
