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

import io.micrometer.observation.Observation;
import java.util.List;
import org.dataloader.BatchLoaderEnvironment;

/**
 * Context that holds information for metadata collection during observations
 * for {@link GraphQlObservationDocumentation#DATA_LOADER data loader operations}.
 *
 * @author Brian Clozel
 * @since 1.4.0
 */
public class DataLoaderObservationContext extends Observation.Context {

	private final List<?> keys;

	private final BatchLoaderEnvironment environment;

	public DataLoaderObservationContext(List<?> keys, BatchLoaderEnvironment environment) {
		this.keys = keys;
		this.environment = environment;
	}

	public List<?> getKeys() {
		return this.keys;
	}

	public BatchLoaderEnvironment getEnvironment() {
		return this.environment;
	}
}
