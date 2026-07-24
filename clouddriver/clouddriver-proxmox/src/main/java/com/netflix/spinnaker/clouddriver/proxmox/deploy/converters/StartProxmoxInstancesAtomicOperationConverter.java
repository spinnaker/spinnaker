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
package com.netflix.spinnaker.clouddriver.proxmox.deploy.converters;

import com.netflix.spinnaker.clouddriver.proxmox.ProxmoxOperation;
import com.netflix.spinnaker.clouddriver.proxmox.deploy.description.ProxmoxResourceDescription;
import com.netflix.spinnaker.clouddriver.proxmox.deploy.ops.StartProxmoxInstancesAtomicOperation;
import org.springframework.stereotype.Component;

@ProxmoxOperation("startInstances")
@Component("proxmoxStartInstances")
public class StartProxmoxInstancesAtomicOperationConverter
    extends AbstractProxmoxAtomicOperationConverter<ProxmoxResourceDescription> {

  public StartProxmoxInstancesAtomicOperationConverter() {
    super(ProxmoxResourceDescription.class, StartProxmoxInstancesAtomicOperation::new);
  }
}
