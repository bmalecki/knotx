/*
 * Knot.x - Reactive microservice assembler
 *
 * Copyright (C) 2016 Cognifide Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cognifide.knotx.server;

import com.cognifide.knotx.api.KnotxConst;
import com.cognifide.knotx.api.RepositoryRequest;
import com.cognifide.knotx.api.RepositoryResponse;
import com.cognifide.knotx.api.TemplateEngineRequest;

import java.io.IOException;
import java.net.URISyntaxException;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.MultiMap;
import io.vertx.rxjava.core.eventbus.EventBus;
import io.vertx.rxjava.core.eventbus.Message;
import io.vertx.rxjava.core.http.HttpServer;
import io.vertx.rxjava.core.http.HttpServerRequest;

public class KnotxServerVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnotxServerVerticle.class);

    private KnotxServerConfiguration configuration;

    private HttpServer httpServer;

    @Override
    public void init(Vertx vertx, Context context) {
        super.init(vertx, context);
        configuration = new KnotxServerConfiguration(config().getJsonObject("config"));
    }

    @Override
    public void start(Future<Void> fut) throws IOException, URISyntaxException {
        LOGGER.debug("Registered <{}>", this.getClass().getSimpleName());
        httpServer = vertx.createHttpServer();
        EventBus eventBus = vertx.eventBus();

        httpServer.requestHandler(
                request -> eventBus.<RepositoryResponse>sendObservable(KnotxConst.TEMPLATE_REPOSITORY_ADDRESS, createRepositoryRequest(request))
                        .doOnNext(this::traceMessage)
                        .subscribe(
                                reply -> {
                                    RepositoryResponse repository = reply.body();
                                    if (repository.isSuccess()) {
                                        eventBus.sendObservable(KnotxConst.TEMPLATE_ENGINE_ADDRESS, createEngineRequest(repository, request))
                                                .subscribe(
                                                        result -> {
                                                            Object body = result.body();
                                                            request.response().end(body.toString());
                                                        },
                                                        error -> {
                                                            LOGGER.error("Error happened", error);
                                                            request.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(error.toString());
                                                        }
                                                );
                                    } else {
                                        request.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end(repository.getReason());
                                    }
                                },
                                error -> LOGGER.error("Error: ", error)
                        )
        ).listen(
                configuration.httpPort(),
                result -> {
                    if (result.succeeded()) {
                        LOGGER.info("Successfully Started");
                        fut.complete();
                    } else {
                        LOGGER.error("Unable to start verticle, reason <{}>", result.cause().getMessage());
                        fut.fail(result.cause());
                    }
                });
    }

    @Override
    public void stop() throws Exception {
        httpServer.close();
    }

    private RepositoryRequest createRepositoryRequest(HttpServerRequest request) {
        return new RepositoryRequest(request.path(), request.headers());
    }

    private TemplateEngineRequest createEngineRequest(RepositoryResponse repositoryResponse, HttpServerRequest request) {
        TemplateEngineRequest templateEngineRequest = new TemplateEngineRequest(repositoryResponse.getData());

        final MultiMap preservedHeaders = MultiMap.caseInsensitiveMultiMap();
        request.headers().names().stream()
                .filter(header -> configuration.serviceCallHeaders().contains(header))
                .forEach(header -> preservedHeaders.add(header, request.headers().get(header)));

        templateEngineRequest.setHeaders(preservedHeaders);

        return templateEngineRequest;
    }

    private void traceMessage(Message<?> message) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Got message from <template-repository> with value <{}>", message.body());
        }
    }
}