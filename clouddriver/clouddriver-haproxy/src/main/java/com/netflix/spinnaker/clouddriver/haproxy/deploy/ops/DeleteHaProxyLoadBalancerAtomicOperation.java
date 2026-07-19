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
import com.netflix.spinnaker.clouddriver.haproxy.deploy.description.DeleteHaProxyLoadBalancerDescription;
import java.io.IOException;
import java.util.List;
import retrofit2.Response;

/** Deletes a frontend inside a transaction; deleting a missing frontend is a no-op. */
public class DeleteHaProxyLoadBalancerAtomicOperation extends AbstractHaProxyAtomicOperation<Void> {

  private static final String PHASE = "DELETE_HAPROXY_LOAD_BALANCER";

  private final DeleteHaProxyLoadBalancerDescription description;

  public DeleteHaProxyLoadBalancerAtomicOperation(
      DeleteHaProxyLoadBalancerDescription description) {
    super(PHASE);
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    String name = description.getLoadBalancerName();
    FrontendApi frontendApi = description.getCredentials().getApi(FrontendApi.class);

    try {
      HaProxyTransactions.run(
          description.getCredentials(),
          transactionId -> {
            updateStatus("Deleting frontend " + name);
            Response<Void> response =
                frontendApi.deleteFrontend(name, transactionId, null, null).execute();
            if (response.code() == 404) {
              updateStatus("Frontend " + name + " does not exist; nothing to delete");
            } else if (!response.isSuccessful()) {
              throw new IllegalStateException(
                  "Deleting frontend " + name + " failed: HTTP " + response.code());
            }
          });
    } catch (IOException e) {
      throw new RuntimeException("Deleting frontend " + name + " failed", e);
    }

    updateStatus("Deleted frontend " + name);
    return null;
  }
}
