/*
 * Copyright 2020-2021 the original author or authors.
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

package org.springframework.graphql.test.tester;

/**
 * Contract to resolve the content of the GraphQL query to be sent
 * by a {@link GraphQlTester}, given a query name.
 *
 * @author Brian Clozel
 * @since 1.0.0
 */
@FunctionalInterface
public interface QueryNameResolver {

	/**
	 * Resolve the content of the GraphQL query from a given query name.
	 * @param queryName the query name
	 * @return the content of the query 
	 */
	String resolveQuery(String queryName);

}
