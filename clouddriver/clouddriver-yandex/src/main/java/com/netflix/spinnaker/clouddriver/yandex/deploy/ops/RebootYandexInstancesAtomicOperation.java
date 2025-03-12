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
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.RebootYandexInstancesDescription;
import com.netflix.spinnaker.clouddriver.yandex.service.YandexCloudFacade;
import java.util.List;

public class RebootYandexInstancesAtomicOperation
    extends AbstractYandexAtomicOperation<RebootYandexInstancesDescription>
    implements AtomicOperation<Void> {

  private static final String BASE_PHASE = YandexCloudFacade.REBOOT_INSTANCES;

  public RebootYandexInstancesAtomicOperation(RebootYandexInstancesDescription description) {
    super(description);
  }

  @Override
  public Void operate(List<Void> priorOutputs) {
    String insances = String.join(", ", description.getInstanceIds());
    status(BASE_PHASE, "Initializing reboot of instances (%s)...", insances);
    for (String instanceId : description.getInstanceIds()) {
      status(BASE_PHASE, "Attempting to reset instance '%s'...", instanceId);
      yandexCloudFacade.restrartInstance(credentials, instanceId);
    }
    status(BASE_PHASE, "Done rebooting instances (%s).", insances);
    return null;
  }
}
