/*
 * Knot.x - Reactive microservice assembler - Http Service Adapter
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
package com.cognifide.knotx.adapter.service.http;

import com.cognifide.knotx.dataobjects.AdapterRequest;
import com.cognifide.knotx.dataobjects.AdapterResponse;
import com.cognifide.knotx.dataobjects.ClientRequest;
import com.cognifide.knotx.junit.rule.KnotxConfiguration;
import com.cognifide.knotx.junit.rule.Logback;
import com.cognifide.knotx.junit.rule.TestVertxDeployer;
import com.cognifide.knotx.junit.util.FileReader;
import com.cognifide.knotx.rxjava.proxy.AdapterProxy;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.rxjava.core.Vertx;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import rx.functions.Action1;

@RunWith(VertxUnitRunner.class)
public class HttpServiceAdapterProxyTest {

  private final static String ADAPTER_ADDRESS = "knotx.adapter.service.http";

  //Test Runner Rule of Verts
  private RunTestOnContext vertx = new RunTestOnContext();

  //Test Runner Rule of Knotx
  private TestVertxDeployer knotx = new TestVertxDeployer(vertx);

  //Junit Rule, sets up logger, prepares verts, starts verticles according to the config (supplied in annotation of test method)
  @Rule
  public RuleChain chain = RuleChain.outerRule(new Logback()).around(vertx).around(knotx);

  @Test
  @KnotxConfiguration("knotx-service-adapter-http-test.json")
  public void callNonExistingService_expectBadRequestResponse(TestContext context) {
    callAdapterServiceWithAssertions(context, "not/existing/service/address",
        adapterResponse -> context.assertTrue(adapterResponse.getResponse().getStatusCode()
            == HttpResponseStatus.INTERNAL_SERVER_ERROR.code()),
        error -> context.fail(error.getMessage()));
  }


  @Test
  @KnotxConfiguration("knotx-service-adapter-http-test.json")
  public void callExistingService_expectOKResponseWithServiceDataProvidedByService1(
      TestContext context) throws Exception {
    final String expected = FileReader.readText("first-response.json");

    callAdapterServiceWithAssertions(context, "/service/mock/first.json",
        adapterResponse -> {
          context.assertTrue(
              adapterResponse.getResponse().getStatusCode() == HttpResponseStatus.OK.code());

          JsonObject serviceResponse = new JsonObject(
              adapterResponse.getResponse().getBody().toString());
          JsonObject expectedResponse = new JsonObject(expected);
          context.assertEquals(serviceResponse, expectedResponse);
        },
        error -> context.fail(error.getMessage()));
  }

  private void callAdapterServiceWithAssertions(TestContext context, String servicePath,
      Action1<AdapterResponse> onSuccess,
      Action1<Throwable> onError) {
    AdapterRequest message = payloadMessage(servicePath);
    Async async = context.async();

    AdapterProxy service = AdapterProxy.createProxy(new Vertx(vertx.vertx()), ADAPTER_ADDRESS);

    service.processObservable(message)
        .subscribe(
            onSuccess,
            onError,
            async::complete
        );
  }

  private AdapterRequest payloadMessage(String servicePath) {
    return new AdapterRequest().setRequest(new ClientRequest())
        .setParams(new JsonObject().put("path", servicePath));
  }

}
