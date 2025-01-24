/*
 * Copyright 2020-2025 the original author or authors.
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

package org.springframework.graphql.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import java.util.Locale;

import org.springframework.graphql.observation.GraphQlObservationDocumentation.DataLoaderLowCardinalityKeyNames;

/**
 * Default implementation for a {@link DataLoaderObservationConvention}
 * extracting information from a {@link DataLoaderObservationContext}.
 *
 * @author Brian Clozel
 * @since 1.4.0
 */
public class DefaultDataLoaderObservationConvention implements DataLoaderObservationConvention {

	private static final String DEFAULT_NAME = "graphql.dataloader";

	private static final KeyValue OUTCOME_SUCCESS = KeyValue.of(DataLoaderLowCardinalityKeyNames.OUTCOME, "SUCCESS");

	private static final KeyValue OUTCOME_ERROR = KeyValue.of(DataLoaderLowCardinalityKeyNames.OUTCOME, "ERROR");

	@Override
	public String getName() {
		return DEFAULT_NAME;
	}

	@Override
	public String getContextualName(DataLoaderObservationContext context) {
		return "graphql dataloader " + context.getKeys().get(0).getClass().getSimpleName().toLowerCase(Locale.ROOT);
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(DataLoaderObservationContext context) {
		return KeyValues.of(outcome(context));
	}

	protected KeyValue outcome(DataLoaderObservationContext context) {
		if (context.getError() != null) {
			return OUTCOME_ERROR;
		}
		return OUTCOME_SUCCESS;
	}

}
