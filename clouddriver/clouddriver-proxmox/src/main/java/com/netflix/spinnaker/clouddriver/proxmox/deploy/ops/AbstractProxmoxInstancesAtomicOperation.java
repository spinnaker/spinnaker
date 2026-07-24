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
package com.netflix.spinnaker.clouddriver.proxmox.deploy.ops;

import com.netflix.spinnaker.clouddriver.proxmox.client.ProxmoxApiService;
import com.netflix.spinnaker.clouddriver.proxmox.deploy.description.ProxmoxResourceDescription;
import com.netflix.spinnaker.clouddriver.proxmox.deploy.ops.ProxmoxServerGroupMembers.Member;
import java.util.List;

/**
 * Base for operations that act on individual instances. Targets are resolved from the description
 * in priority order: explicit {@code instanceIds} (VM/container names), then {@code
 * serverGroupName} (all members), then a single legacy {@code node}/{@code vmid} pair.
 */
public abstract class AbstractProxmoxInstancesAtomicOperation
    extends AbstractProxmoxAtomicOperation<Void> {

  protected final ProxmoxResourceDescription description;

  protected AbstractProxmoxInstancesAtomicOperation(
      String phase, ProxmoxResourceDescription description) {
    super(phase);
    this.description = description;
  }

  /** Human-readable verb for status messages, e.g. "Terminating". */
  protected abstract String verb();

  /** Applies the operation to a single instance. */
  protected abstract void applyTo(ProxmoxApiService api, String node, int vmid, String vmType);

  @Override
  public Void operate(List priorOutputs) {
    ProxmoxApiService api = description.getApiService();
    String region =
        description.getRegion() != null ? description.getRegion() : description.getNode();

    if (description.getInstanceIds() != null && !description.getInstanceIds().isEmpty()) {
      List<Member> targets =
          ProxmoxServerGroupMembers.findByNames(api, description.getInstanceIds(), region);
      if (targets.isEmpty()) {
        throw new IllegalStateException(
            "No instances found matching " + description.getInstanceIds());
      }
      for (Member target : targets) {
        updateStatus(verb() + " instance " + target.name() + " (vmid " + target.vmid() + ")");
        applyTo(api, target.node(), target.vmid(), target.vmType());
      }
    } else if (description.getServerGroupName() != null) {
      List<Member> members =
          ProxmoxServerGroupMembers.resolve(api, description.getServerGroupName(), region);
      updateStatus(
          verb()
              + " all "
              + members.size()
              + " instance(s) of "
              + description.getServerGroupName());
      for (Member member : members) {
        applyTo(api, member.node(), member.vmid(), member.vmType());
      }
    } else {
      applyTo(api, description.getNode(), description.getVmid(), description.getVmType());
    }
    return null;
  }
}
