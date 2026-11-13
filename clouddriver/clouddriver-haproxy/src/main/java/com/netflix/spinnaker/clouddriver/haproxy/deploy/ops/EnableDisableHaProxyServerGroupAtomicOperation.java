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

import com.netflix.spinnaker.clouddriver.haproxy.dataplane.api.ServerApi;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.model.RuntimeServer;
import com.netflix.spinnaker.clouddriver.haproxy.deploy.description.EnableDisableHaProxyServerGroupDescription;
import java.util.List;

/**
 * Sets the admin state of every server in a backend via the runtime API: {@code ready} on enable,
 * {@code maint} on disable. Runtime changes take effect immediately without a configuration
 * transaction or reload.
 */
public class EnableDisableHaProxyServerGroupAtomicOperation
    extends AbstractHaProxyAtomicOperation<Void> {

  private final EnableDisableHaProxyServerGroupDescription description;
  private final boolean enable;

  public EnableDisableHaProxyServerGroupAtomicOperation(
      EnableDisableHaProxyServerGroupDescription description, boolean enable) {
    super(enable ? "ENABLE_HAPROXY_SERVER_GROUP" : "DISABLE_HAPROXY_SERVER_GROUP");
    this.description = description;
    this.enable = enable;
  }

  @Override
  public Void operate(List priorOutputs) {
    String backend = description.getServerGroupName();
    RuntimeServer.AdminStateEnum targetState =
        enable ? RuntimeServer.AdminStateEnum.READY : RuntimeServer.AdminStateEnum.MAINT;
    ServerApi serverApi = description.getCredentials().getApi(ServerApi.class);

    updateStatus((enable ? "Enabling" : "Disabling") + " all servers in backend " + backend);
    List<RuntimeServer> servers = execute(serverApi.getAllRuntimeServer(backend));
    for (RuntimeServer server : servers) {
      if (server.getName() == null || targetState.equals(server.getAdminState())) {
        continue;
      }
      updateStatus("Setting server " + server.getName() + " to " + targetState.getValue());
      server.setAdminState(targetState);
      execute(serverApi.replaceRuntimeServer(backend, server.getName(), server));
    }

    updateStatus("All servers in backend " + backend + " set to " + targetState.getValue());
    return null;
  }
}
