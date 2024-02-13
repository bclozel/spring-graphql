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
import java.util.Collections;
import java.util.Map;

import graphql.ExecutionResult;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.support.SerializableGraphQlRequest;
import org.springframework.http.HttpCookie;
import org.springframework.lang.Nullable;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.IdGenerator;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * GraphQL handler that supports the
 * <a href="https://github.com/graphql/graphql-over-http/blob/main/rfcs/GraphQLOverSSE.md">GraphQL
 * Server-Sent Events Protocol</a> and to be exposed as a WebMvc functional endpoint via
 * {@link org.springframework.web.servlet.function.RouterFunctions}.
 *
 * @author Brian Clozel
 * @since 1.3.0
 */
public class GraphQlSseHandler {

    private static final Log logger = LogFactory.getLog(GraphQlSseHandler.class);

    private final WebGraphQlHandler graphQlHandler;

    private final IdGenerator idGenerator = new AlternativeJdkIdGenerator();


    public GraphQlSseHandler(WebGraphQlHandler graphQlHandler) {
        Assert.notNull(graphQlHandler, "WebGraphQlHandler is required");
        this.graphQlHandler = graphQlHandler;
    }

    /**
     * Handle GraphQL requests over HTTP using the Server-Sent Events protocol.
     *
     * @param serverRequest the incoming HTTP request
     * @return the HTTP response
     * @throws ServletException may be raised when reading the request body, e.g.
     * {@link HttpMediaTypeNotSupportedException}.
     */
    public ServerResponse handleRequest(ServerRequest serverRequest) throws ServletException {

        WebGraphQlRequest graphQlRequest = new WebGraphQlRequest(
                serverRequest.uri(), serverRequest.headers().asHttpHeaders(), initCookies(serverRequest),
                serverRequest.attributes(), readBody(serverRequest), this.idGenerator.generateId().toString(),
                LocaleContextHolder.getLocale());

        if (logger.isDebugEnabled()) {
            logger.debug("Executing: " + graphQlRequest);
        }
        return ServerResponse.sse(sseBuilder -> {
            this.graphQlHandler.handleRequest(graphQlRequest)
                    .flatMapMany(this::handleResponse)
                    .publishOn(Schedulers.newSingle("GraphQL-SSE-" + graphQlRequest.getId())) // Serial blocking send via single thread
                    .subscribe(new SendMessageSubscriber(graphQlRequest.getId(), sseBuilder));
        });
    }


    @SuppressWarnings("unchecked")
    private Publisher<Map<String, Object>> handleResponse(WebGraphQlResponse response) {
        if (logger.isDebugEnabled()) {
            logger.debug("Execution result ready"
                    + (!CollectionUtils.isEmpty(response.getErrors()) ? " with errors: " + response.getErrors() : "")
                    + ".");
        }
        if (response.getData() instanceof Publisher) {
            // Subscription
            return Flux.from((Publisher<ExecutionResult>) response.getData()).map(ExecutionResult::toSpecification);
        }
        // Single response (query or mutation) that may contain errors
        return Flux.just(response.toMap());
    }

    private static MultiValueMap<String, HttpCookie> initCookies(ServerRequest serverRequest) {
        MultiValueMap<String, Cookie> source = serverRequest.cookies();
        MultiValueMap<String, HttpCookie> target = new LinkedMultiValueMap<>(source.size());
        source.values().forEach(cookieList -> cookieList.forEach(cookie -> {
            HttpCookie httpCookie = new HttpCookie(cookie.getName(), cookie.getValue());
            target.add(cookie.getName(), httpCookie);
        }));
        return target;
    }

    private static GraphQlRequest readBody(ServerRequest request) throws ServletException {
        try {
            return request.body(SerializableGraphQlRequest.class);
        } catch (IOException ex) {
            throw new ServerWebInputException("I/O error while reading request body", null, ex);
        }
    }

    private static class SendMessageSubscriber extends BaseSubscriber<Map<String, Object>> {

        final String id;

        final ServerResponse.SseBuilder sseBuilder;

        public SendMessageSubscriber(String id, ServerResponse.SseBuilder sseBuilder) {
            this.id = id;
            this.sseBuilder = sseBuilder;
        }

        @Override
        protected void hookOnNext(Map<String, Object> value) {
            write("next", value);
        }

        @Override
        protected void hookOnComplete() {
            write("complete", null);
            this.sseBuilder.complete();
        }

        private void write(String eventName, @Nullable Map<String, Object> data) {
            try {
                this.sseBuilder.event(eventName);
                if (data != null) {
                    this.sseBuilder.data(data);
                }
                else {
                    this.sseBuilder.data(Collections.emptyMap());
                }
            } catch (IOException exception) {
                if (logger.isErrorEnabled()) {
                    logger.error("Closing connection due to exception for " + this.id, exception);
                }
                this.onError(exception);
            }
        }

        @Override
        protected void hookOnError(Throwable throwable) {
            this.sseBuilder.error(throwable);
        }
    }

}
