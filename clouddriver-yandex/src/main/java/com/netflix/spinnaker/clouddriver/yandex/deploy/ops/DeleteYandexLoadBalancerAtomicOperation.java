/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.deploy.ops;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.DeleteYandexLoadBalancerDescription;
import java.util.List;

public class DeleteYandexLoadBalancerAtomicOperation
    extends AbstractYandexAtomicOperation<DeleteYandexLoadBalancerDescription>
    implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DELETE_LOAD_BALANCER";

  public DeleteYandexLoadBalancerAtomicOperation(DeleteYandexLoadBalancerDescription description) {
    super(description);
  }

  @Override
  public Void operate(List<Void> priorOutputs) {
    String name = description.getLoadBalancerName();
    status(BASE_PHASE, "Initializing deletion of load balancer '%s'+ ...", name);
    String loadBalancerId =
        single(yandexCloudFacade.getLoadBalancerIds(credentials, name))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        status(
                            BASE_PHASE,
                            "Found none of more than one load balancer with name '%s'!",
                            name)));
    yandexCloudFacade.deleteLoadBalancer(credentials, loadBalancerId);
    status(BASE_PHASE, "Done deleting load balancer '%s'.", name);
    return null;
  }
}
