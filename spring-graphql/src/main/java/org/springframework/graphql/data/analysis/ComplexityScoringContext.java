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

import java.util.Map;
import java.util.Objects;

import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.language.SelectionSetContainer;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLFieldDefinition;

public class ComplexityScoringContext {

	private final QueryVisitorFieldEnvironment environment;

	ComplexityScoringContext(QueryVisitorFieldEnvironment environment) {
		this.environment = environment;
	}

	public FieldCoordinates getFieldCoordinates() {
		return FieldCoordinates.coordinates(this.environment.getFieldsContainer(), this.environment.getFieldDefinition());
	}

	public GraphQLFieldDefinition getFieldDefinition() {
		return this.environment.getFieldDefinition();
	}

	public DataFetcher<?> resolveDataFetcher() {
		return this.environment.getSchema().getCodeRegistry().getDataFetcher(getFieldCoordinates(), getFieldDefinition());
	}

	public SelectionSetContainer<?> getFieldSelectionSet() {
		return this.environment.getSelectionSetContainer();
	}

	public Map<String, Object> getArguments() {
		return this.environment.getArguments();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ComplexityScoringContext that = (ComplexityScoringContext) o;
		return Objects.equals(this.environment, that.environment);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(environment);
	}
}
