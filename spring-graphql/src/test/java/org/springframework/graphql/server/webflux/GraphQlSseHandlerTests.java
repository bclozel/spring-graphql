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

package org.springframework.graphql.server.webflux;


import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.BookSource;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.server.support.SerializableGraphQlRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerSentEventHttpMessageWriter;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GraphQlSseHandler}.
 *
 * @author Brian Clozel
 */
class GraphQlSseHandlerTests {

    private static final List<HttpMessageWriter<?>> MESSAGE_WRITERS = Collections.singletonList(new ServerSentEventHttpMessageWriter(new Jackson2JsonEncoder()));

    private final MockServerHttpRequest httpRequest = MockServerHttpRequest.post("/graphql")
            .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM).build();


    private final GraphQlSseHandler sseHandler = new GraphQlSseHandler(GraphQlSetup.schemaResource(BookSource.schema)
            .queryFetcher("bookById", (env) -> BookSource.getBookWithoutAuthor(1L))
            .queryFetcher("booksById", (env) -> Flux.error(new IllegalArgumentException("error")))
            .subscriptionFetcher("bookSearch", environment -> {
                String author = environment.getArgument("author");
                return Flux.fromIterable(BookSource.books())
                        .filter((book) -> book.getAuthor().getFullName().contains(author));
            })
            .toWebGraphQlHandler());

    @Test
    void shouldWriteSingleEventForQuery() {
        SerializableGraphQlRequest request = initRequest("{ bookById(id: 42) {name} }");
        MockServerHttpResponse httpResponse = handleRequest(this.httpRequest, this.sseHandler, request);

        assertThat(httpResponse.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();
        assertThat(httpResponse.getBodyAsString().block()).isEqualTo(
                """
                        event:next
                        data:{"data":{"bookById":{"name":"Nineteen Eighty-Four"}}}
                        
                        event:complete
                        data:{}
                        
                        """);
    }

    @Test
    void shouldSupportErrorQueryWhenError() {
        SerializableGraphQlRequest request = initRequest("{ booksById(id: [42]) {name} }");
        MockServerHttpResponse httpResponse = handleRequest(this.httpRequest, this.sseHandler, request);

        assertThat(httpResponse.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();
        assertThat(httpResponse.getBodyAsString().block()).containsSubsequence(
                "event:next",
                "data:{\"errors\":[{\"message\":\"INTERNAL_ERROR for ",
                """
                event:complete
                data:{}
                
                """);
    }

    @Test
    void shouldWriteMultipleEventsForSubscription() {
        SerializableGraphQlRequest request = initRequest("subscription TestSubscription { bookSearch(author:\"Orwell\") { id name } }");
        MockServerHttpResponse httpResponse = handleRequest(this.httpRequest, this.sseHandler, request);

        assertThat(httpResponse.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();
        assertThat(httpResponse.getBodyAsString().block()).isEqualTo(
                """
                        event:next
                        data:{"data":{"bookSearch":{"id":"1","name":"Nineteen Eighty-Four"}}}
                        
                        event:next
                        data:{"data":{"bookSearch":{"id":"5","name":"Animal Farm"}}}
                        
                        event:complete
                        data:{}

                        """);
    }

    private static SerializableGraphQlRequest initRequest(String document) {
        SerializableGraphQlRequest request = new SerializableGraphQlRequest();
        request.setQuery(document);
        return request;
    }

    private MockServerHttpResponse handleRequest(
            MockServerHttpRequest httpRequest, GraphQlSseHandler handler, GraphQlRequest body) {

        MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);

        MockServerRequest serverRequest = MockServerRequest.builder()
                .exchange(exchange)
                .uri(((ServerWebExchange) exchange).getRequest().getURI())
                .method(((ServerWebExchange) exchange).getRequest().getMethod())
                .headers(((ServerWebExchange) exchange).getRequest().getHeaders())
                .body(Mono.just(body));

        handler.handleRequest(serverRequest)
                .flatMap(response -> response.writeTo(exchange, new DefaultContext()))
                .block();

        return exchange.getResponse();
    }


    private static class DefaultContext implements ServerResponse.Context {

        @Override
        public List<HttpMessageWriter<?>> messageWriters() {
            return MESSAGE_WRITERS;
        }

        @Override
        public List<ViewResolver> viewResolvers() {
            return Collections.emptyList();
        }

    }


}