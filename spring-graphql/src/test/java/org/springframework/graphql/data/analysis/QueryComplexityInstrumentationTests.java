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
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.graphql.Address;
import org.springframework.graphql.Author;
import org.springframework.graphql.Book;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.BookStore;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.Genre;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.OnlineStore;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestExecutionGraphQlService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer;
import org.springframework.graphql.data.pagination.ConnectionAdapter;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.graphql.execution.DefaultBatchLoaderRegistry;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link QueryComplexityInstrumentation}.
 */
class QueryComplexityInstrumentationTests {

	private ComplexityScore totalScore;

	private final QueryComplexityInstrumentation instrumentation = new QueryComplexityInstrumentation(((graphQLContext, complexityScore) -> this.totalScore = complexityScore));

	@Nested
	class DefaultStrategyTests {

		@Test
		void shouldScoreDataFetcher() {
			String document = """
					{
						bookById(id: "42") {
							id
							name
						}
					}
					""";

			executeQuery(document, SimpleBookController.class);
			assertThat(totalScore).isNotNull();
			assertThat(totalScore.calculate()).isEqualTo(1);
		}

		@Test
		void shouldScoreNestedDataFetcher() {
			String document = """
					{
						bookById(id: "42") {
							id
							name
							author {
								firstName
							}
						}
					}
					""";

			executeQuery(document, SimpleBookController.class);
			assertThat(totalScore).isNotNull();
			assertThat(totalScore.calculate()).isEqualTo(1 + 1);
		}

		@Test
		void shouldScoreNestedCollectionDataFetcher() {
			String document = """
					{
						bookById(id: "42") {
							id
							name
							genres {
								name
							}
						}
					}
					""";

			executeQuery(document, SimpleBookController.class);
			assertThat(totalScore).isNotNull();
			assertThat(totalScore.calculate()).isEqualTo(2);
		}

		@Test
		void shouldScoreCollectionDataFetcher() {
			String document = """
					{
						booksById(ids: ["1", "2", "3"]) {
							id
							name
						}
					}
					""";

			executeQuery(document, SimpleBookController.class);
			assertThat(totalScore).isNotNull();
			assertThat(totalScore.calculate()).isEqualTo(1);
		}

		@Test
		void shouldScoreCollectionWithNestedDataFetcher() {
			String document = """
					{
						booksById(ids: ["1", "2", "3"]) {
							id
							name
							author {
								firstName
								lastName
							}
						}
					}
					""";

			executeQuery(document, SimpleBookController.class);
			assertThat(totalScore).isNotNull();
			assertThat(totalScore.calculate()).isEqualTo(1 + 10);
		}

		@Test
		void shouldScoreConnectionWithNestedDataFetcher() {
			String document = """
					{
						books(first: 5, after: "101") {
							edges {
								node {
									id
									name
									author {
										firstName
										lastName
									}
								}
							}
						}
					}
					""";

			executeQuery(document, SimpleBookController.class);
			assertThat(totalScore).isNotNull();
			assertThat(totalScore.calculate()).isEqualTo(1 + 5);
		}

		@Test
		void shouldUseHighestFragmentScore() {
			String document = """
					{
						books(first: 5, after: "101") {
							edges {
								node {
									id
									name
									store {
										... on BookStore {
											name
											address {
												streetAddress
												zipCode
												country
											}
										}
										... on OnlineStore {
											name
											url
										}
									}
								}
							}
						}
					}
					""";

			executeQuery(document, SimpleBookController.class);
			assertThat(totalScore).isNotNull();
			assertThat(totalScore.calculate()).isEqualTo(1 + 5);
		}

		@Test
		void shouldSupportFragments() {
			String document = """
					query withFragments {
						bookById(id: "42") {
							...bookFields
						}
					}

					fragment bookFields on Book {
						id
						name
						genres {
							name
						}
					}
					""";
			executeQuery(document, SimpleBookController.class);
			assertThat(totalScore).isNotNull();
			assertThat(totalScore.calculate()).isEqualTo(1 + 1);
		}

		@Controller
		static class SimpleBookController {

			@QueryMapping
			public Book bookById(@Argument Long id) {
				return BookSource.getBook(id);
			}

			@QueryMapping
			public List<Book> booksById(@Argument List<Long> ids) {
				return ids.stream().map(BookSource::getBook).toList();
			}

			@QueryMapping
			public List<Book> books(@Argument Integer first, @Argument String after) {
				return BookSource.books().stream().limit(first).toList();
			}

			@SchemaMapping
			public Author author(Book book) {
				return BookSource.getAuthor(book.getId());
			}

			@SchemaMapping
			public List<Genre> genres(Book book) {
				return BookSource.getGenres(book.getGenreIds());
			}

			@SchemaMapping
			public Address address(BookStore bookStore) {
				return BookSource.getStoreAddress(bookStore.id());
			}

			// overriding property data fetcher for testing purposes.
			@SchemaMapping
			public String url(OnlineStore store) {
				return store.url();
			}

		}
	}

	@Nested
	class CustomStrategiesTests {

		@Test
		void shouldScoreDataFetcher() {
			String document = """
					{
						bookById(id: "42") {
							id
							name
						}
					}
					""";

			executeQuery(document, CustomBookController.class);
			assertThat(totalScore).isNotNull();
			assertThat(totalScore.calculate()).isEqualTo(5);
		}

		@Test
		void shouldScoreNestedDataFetcher() {
			String document = """
					{
						bookById(id: "42") {
							id
							name
							author {
								firstName
							}
						}
					}
					""";

			executeQuery(document, CustomBookController.class);
			assertThat(totalScore).isNotNull();
			assertThat(totalScore.calculate()).isEqualTo(5 + 5);
		}

		@Test
		void shouldScoreCollectionWithNestedDataFetcher() {
			String document = """
					{
						booksById(ids: ["1", "2", "3"]) {
							id
							name
							author {
								firstName
								lastName
							}
						}
					}
					""";

			executeQuery(document, CustomBookController.class);
			assertThat(totalScore).isNotNull();
			assertThat(totalScore.calculate()).isEqualTo(1 + 20 * 5);
		}

		@Test
		void shouldUseHighestFragmentScore() {
			String document = """
					{
						books(first: 5, after: "101") {
							edges {
								node {
									id
									name
									store {
										... on BookStore {
											name
											address {
												streetAddress
												zipCode
												country
											}
										}
										... on OnlineStore {
											name
											url
										}
									}
								}
							}
						}
					}
					""";

			executeQuery(document, CustomBookController.class);
			assertThat(totalScore).isNotNull();
			assertThat(totalScore.calculate()).isEqualTo(5 + 5 * 5);
		}


		@Controller
		static class CustomBookController {

			@QueryMapping
			@SchemaComplexity(cost = 5)
			public Book bookById(@Argument Long id) {
				return BookSource.getBook(id);
			}

			@QueryMapping
			@SchemaComplexity(cost = 1, elementCount = 20)
			public List<Book> booksById(@Argument List<Long> ids) {
				return ids.stream().map(BookSource::getBook).toList();
			}

			@QueryMapping
			@SchemaComplexity(cost = 5)
			public List<Book> books(@Argument Integer first, @Argument String after) {
				return BookSource.books().stream().limit(first).toList();
			}

			@SchemaMapping
			@SchemaComplexity(cost = 5)
			public Author author(Book book) {
				return BookSource.getAuthor(book.getId());
			}

			@SchemaMapping
			public List<Genre> genres(Book book) {
				return BookSource.getGenres(book.getGenreIds());
			}

			@SchemaMapping
			@SchemaComplexity(cost = 5)
			public Address address(BookStore bookStore) {
				return BookSource.getStoreAddress(bookStore.id());
			}

			// overriding property data fetcher for testing purposes.
			@SchemaMapping
			@SchemaComplexity(cost = 1)
			public String url(OnlineStore store) {
				return store.url();
			}

		}
	}


	private void executeQuery(String document, Class<?> controllerType) {
		Mono<ExecutionGraphQlResponse> responseMono = graphQlService(controllerType).execute(document);
		ResponseHelper responseHelper = ResponseHelper.forResponse(responseMono);
		if (responseHelper.errorCount() > 0) {
			ResponseHelper.Error error = responseHelper.error(0);
			throw new IllegalStateException(String.format("Error processing request: %s", error.message()));
		}
	}


	private TestExecutionGraphQlService graphQlService(Class<?> controller) {
		BatchLoaderRegistry registry = new DefaultBatchLoaderRegistry();
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(controller);
		context.registerBean(BatchLoaderRegistry.class, () -> registry);
		context.refresh();
		AnnotatedControllerConfigurer configurer = new AnnotatedControllerConfigurer();
		configurer.setExecutor(new SimpleAsyncTaskExecutor());
		configurer.setApplicationContext(context);
		configurer.afterPropertiesSet();
		GraphQlSetup setup = GraphQlSetup.schemaResource(new ClassPathResource("analysis/schema.graphqls"))
				.runtimeWiring(configurer)
				.connectionSupport(new ListConnectionAdapter())
				.instrumentation(this.instrumentation);
		return setup.dataLoaders(registry).toGraphQlService();
	}

	private static class ListConnectionAdapter implements ConnectionAdapter {

		private int initialOffset = 0;

		private boolean hasNext = false;

		public void setInitialOffset(int initialOffset) {
			this.initialOffset = initialOffset;
		}

		public void setHasNext(boolean hasNext) {
			this.hasNext = hasNext;
		}

		@Override
		public boolean supports(Class<?> containerType) {
			return Collection.class.isAssignableFrom(containerType);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T> List<T> getContent(Object container) {
			return (List<T>) container;
		}

		@Override
		public boolean hasPrevious(Object container) {
			return (this.initialOffset != 0);
		}

		@Override
		public boolean hasNext(Object container) {
			return this.hasNext;
		}

		@Override
		public String cursorAt(Object container, int index) {
			return "O_" + (this.initialOffset + index);
		}

	}

}
