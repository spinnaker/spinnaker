/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.haproxy.deploy.ops;

import com.netflix.spinnaker.clouddriver.haproxy.client.HaProxyTransactions;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.api.FrontendApi;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.model.Bind;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.model.Frontend;
import com.netflix.spinnaker.clouddriver.haproxy.deploy.description.UpsertHaProxyLoadBalancerDescription;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Response;

/** Creates or replaces a frontend (full section, binds included) inside a transaction. */
public class UpsertHaProxyLoadBalancerAtomicOperation
    extends AbstractHaProxyAtomicOperation<Map<String, Object>> {

  private static final String PHASE = "UPSERT_HAPROXY_LOAD_BALANCER";

  private final UpsertHaProxyLoadBalancerDescription description;

  public UpsertHaProxyLoadBalancerAtomicOperation(
      UpsertHaProxyLoadBalancerDescription description) {
    super(PHASE);
    this.description = description;
  }

  @Override
  public Map<String, Object> operate(List priorOutputs) {
    String name = description.getName();
    FrontendApi frontendApi = description.getCredentials().getApi(FrontendApi.class);
    Frontend frontend = buildFrontend();

    try {
      HaProxyTransactions.run(
          description.getCredentials(),
          transactionId -> {
            Response<Frontend> existing =
                frontendApi.getFrontend(name, transactionId, false).execute();
            if (existing.isSuccessful()) {
              updateStatus("Replacing frontend " + name);
              HaProxyTransactions.execute(
                  frontendApi.replaceFrontend(name, frontend, transactionId, null, null, true));
            } else if (existing.code() == 404) {
              updateStatus("Creating frontend " + name);
              HaProxyTransactions.execute(
                  frontendApi.createFrontend(frontend, transactionId, null, null, true));
            } else {
              throw new IllegalStateException(
                  "Looking up frontend " + name + " failed: HTTP " + existing.code());
            }
          });
    } catch (IOException e) {
      throw new RuntimeException("Upserting frontend " + name + " failed", e);
    }

    updateStatus("Upserted frontend " + name);
    String region = description.getCredentials().getRegion();
    return Map.of("loadBalancers", Map.of(region, Map.of("name", name)));
  }

  private Frontend buildFrontend() {
    Frontend frontend =
        new Frontend()
            .name(description.getName())
            .mode(Frontend.ModeEnum.fromValue(description.getMode()))
            .defaultBackend(description.getDefaultBackend());
    if (description.getMetadata() != null) {
      frontend.setMetadata(description.getMetadata());
    }
    if (description.getBinds() != null) {
      Map<String, Bind> binds = new LinkedHashMap<>();
      description
          .getBinds()
          .forEach(
              (bindName, spec) -> {
                Bind bind =
                    new Bind().name(bindName).address(spec.getAddress()).port(spec.getPort());
                if (spec.getSsl() != null) {
                  bind.setSsl(spec.getSsl());
                }
                binds.put(bindName, bind);
              });
      frontend.setBinds(binds);
    }
    return frontend;
  }
}
