/*
 * Copyright 2002-2021 the original author or authors.
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
package org.springframework.graphql.web;

import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQlService;
import org.springframework.graphql.execution.ThreadLocalAccessor;

/**
 * Contract to handle a GraphQL over HTTP or WebSocket request that forms the
 * basis of a {@link WebInterceptor} delegation chain.
 */
public interface WebGraphQlHandler {

	/**
	 * Perform request execution for the given input and return the result.
	 *
	 * @param input the GraphQL request input container
	 * @return the execution result
	 */
	Mono<WebOutput> handle(WebInput input);


	/**
	 * Provides access to a builder to create a {@link WebGraphQlHandler} instance.
	 * @param graphQlService the {@link GraphQlService} to use for actual
	 * execution of the request.
	 */
	static Builder builder(GraphQlService graphQlService) {
		return new DefaultWebGraphQlHandlerBuilder(graphQlService);
	}


	/**
	 * Builder for {@link WebGraphQlHandler} that represents a {@link WebInterceptor}
	 * chain followed by a {@link GraphQlService}.
	 */
	interface Builder {

		/**
		 * Configure interceptors to be invoked before the target {@code GraphQlService}.
		 * @param interceptors the interceptors to add
		 */
		Builder interceptors(List<WebInterceptor> interceptors);

		/**
		 * Configure accessors for ThreadLocal variables to use to extract
		 * ThreadLocal values at the Web framework level, have those propagated
		 * and re-established at the DataFetcher level.
		 * @param accessors the accessors to add
		 */
		Builder threadLocalAccessors(List<ThreadLocalAccessor> accessors);

		/**
		 * Build the {@link WebGraphQlHandler} instance.
		 */
		WebGraphQlHandler build();
	}

}
