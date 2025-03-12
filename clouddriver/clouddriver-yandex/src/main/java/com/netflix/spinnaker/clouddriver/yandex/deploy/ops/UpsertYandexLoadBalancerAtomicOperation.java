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
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.UpsertYandexLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.yandex.service.YandexCloudFacade;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UpsertYandexLoadBalancerAtomicOperation
    extends AbstractYandexAtomicOperation<UpsertYandexLoadBalancerDescription>
    implements AtomicOperation<Map<String, Map<String, Map<String, String>>>> {

  private static final String BASE_PHASE = YandexCloudFacade.UPSERT_LOAD_BALANCER;

  public UpsertYandexLoadBalancerAtomicOperation(UpsertYandexLoadBalancerDescription description) {
    super(description);
  }

  @Override
  public Map<String, Map<String, Map<String, String>>> operate(
      List<Map<String, Map<String, Map<String, String>>>> priorOutputs) {
    String name = description.getName();
    String id = description.getId();

    status(BASE_PHASE, "Initializing upsert of load balancer '%s'...", name);
    if (id == null || id.isEmpty()) {
      status(BASE_PHASE, "Creating load balancer '%s'...", name);
      yandexCloudFacade.createLoadBalancer(credentials, description);
      status(BASE_PHASE, "Done creating load balancer '%s'.", name);
    } else {
      status(BASE_PHASE, "Updating load balancer '%s'...", name);
      yandexCloudFacade.updateLoadBalancer(id, credentials, description);
      status(BASE_PHASE, "Done updating load balancer '%s'.", name);
    }
    status(BASE_PHASE, "Done upserting load balancer '%s'.", name);

    return Collections.singletonMap(
        "loadBalancers",
        Collections.singletonMap(
            YandexCloudProvider.REGION, Collections.singletonMap("name", name)));
  }
}
