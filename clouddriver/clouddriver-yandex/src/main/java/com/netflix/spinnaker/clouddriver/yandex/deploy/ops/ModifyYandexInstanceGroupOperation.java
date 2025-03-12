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
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.YandexInstanceGroupDescription;
import com.netflix.spinnaker.clouddriver.yandex.service.YandexCloudFacade;
import java.util.List;

public class ModifyYandexInstanceGroupOperation
    extends AbstractYandexAtomicOperation<YandexInstanceGroupDescription>
    implements AtomicOperation<Void> {

  private static final String BASE_PHASE = YandexCloudFacade.MODIFY_INSTANCE_GROUP;

  public ModifyYandexInstanceGroupOperation(YandexInstanceGroupDescription description) {
    super(description);
  }

  @Override
  public Void operate(List<Void> priorOutputs) {
    String serverGroupName = description.getName();
    status(BASE_PHASE, "Initializing operation...");
    description.saturateLabels();
    status(BASE_PHASE, "Resolving server group identifier '%s'...", serverGroupName);
    String instanceGroupId =
        single(yandexCloudFacade.getServerGroupIds(credentials, serverGroupName))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        status(
                            BASE_PHASE,
                            "Found nothing or more than one server group '%s'.",
                            serverGroupName)));
    status(BASE_PHASE, "Composing server group '%s'...", serverGroupName);
    yandexCloudFacade.updateInstanceGroup(credentials, instanceGroupId, description);
    status(BASE_PHASE, "Done updating server group '%s'.", serverGroupName);
    return null;
  }
}
