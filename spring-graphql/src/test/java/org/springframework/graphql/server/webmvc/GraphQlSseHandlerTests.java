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

package org.springframework.graphql.server.webmvc;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.graphql.BookSource;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.function.AsyncServerResponse;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests for {@link GraphQlSseHandler}.
 *
 * @author Brian Clozel
 */
class GraphQlSseHandlerTests {

    private static final List<HttpMessageConverter<?>> MESSAGE_READERS =
            Collections.singletonList(new MappingJackson2HttpMessageConverter());

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
    void shouldWriteSingleEventForQuery() throws Exception {
        MockHttpServletRequest request = createServletRequest("{ \"query\": \"{ bookById(id: 42) {name} }\"}");
        MockHttpServletResponse response = handleRequest(request, this.sseHandler);

        assertThat(response.getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(response.getContentAsString()).isEqualTo(
                """
                        event:next
                        data:{"data":{"bookById":{"name":"Nineteen Eighty-Four"}}}
                        
                        event:complete
                        data:{}
                        
                        """);
    }

    @Test
    void shouldSupportErrorQueryWhenError() throws Exception {
        MockHttpServletRequest request = createServletRequest("{ \"query\": \"{ booksById(id: [42]) {name} }\"}");
        MockHttpServletResponse response = handleRequest(request, this.sseHandler);

        assertThat(response.getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(response.getContentAsString()).containsSubsequence(
                "event:next",
                "data:{\"errors\":[{\"message\":\"INTERNAL_ERROR for ",
                """
                event:complete
                data:{}
                
                """);
    }

    @Test
    void shouldWriteMultipleEventsForSubscription() throws Exception {
        MockHttpServletRequest request = createServletRequest("""
                {
                    "query": "subscription TestSubscription { bookSearch(author:\\\"Orwell\\\") { id name } }"
                }
                """);
        MockHttpServletResponse response = handleRequest(request, this.sseHandler);

        assertThat(response.getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(response.getContentAsString()).isEqualTo(
                """
                        event:next
                        data:{"data":{"bookSearch":{"id":"1","name":"Nineteen Eighty-Four"}}}
                        
                        event:next
                        data:{"data":{"bookSearch":{"id":"5","name":"Animal Farm"}}}
                        
                        event:complete
                        data:{}

                        """);
    }

    private MockHttpServletRequest createServletRequest(String query) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/");
        servletRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);
        servletRequest.setContent(query.getBytes(StandardCharsets.UTF_8));
        servletRequest.addHeader("Accept", MediaType.TEXT_EVENT_STREAM_VALUE);
        servletRequest.setAsyncSupported(true);
        return servletRequest;
    }

    private MockHttpServletResponse handleRequest(
            MockHttpServletRequest servletRequest, GraphQlSseHandler handler) throws ServletException, IOException {

        ServerRequest request = ServerRequest.create(servletRequest, MESSAGE_READERS);
        ServerResponse response = handler.handleRequest(request);
        if (response instanceof AsyncServerResponse asyncResponse) {
            asyncResponse.block();
        }

        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        response.writeTo(servletRequest, servletResponse, new DefaultContext());
        await().atMost(Duration.ofSeconds(3))
                .until(() -> servletResponse.getContentAsString().contains("complete"));
        return servletResponse;
    }


    private static class DefaultContext implements ServerResponse.Context {

        @Override
        public List<HttpMessageConverter<?>> messageConverters() {
            return MESSAGE_READERS;
        }

    }

}