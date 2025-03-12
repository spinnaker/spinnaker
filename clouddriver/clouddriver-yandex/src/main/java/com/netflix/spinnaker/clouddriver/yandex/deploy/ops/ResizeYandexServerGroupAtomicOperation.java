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
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.ResizeYandexServerGroupDescription;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.service.YandexCloudFacade;
import java.util.List;

public class ResizeYandexServerGroupAtomicOperation
    extends AbstractYandexAtomicOperation<ResizeYandexServerGroupDescription>
    implements AtomicOperation<Void> {

  public static final String BASE_PHASE = YandexCloudFacade.RESIZE_SERVER_GROUP;

  public ResizeYandexServerGroupAtomicOperation(ResizeYandexServerGroupDescription description) {
    super(description);
  }

  @Override
  public Void operate(List<Void> priorOutputs) {
    String serverGroupName = description.getServerGroupName();
    status(BASE_PHASE, "Initializing resize of server group '%s'...", serverGroupName);
    YandexCloudServerGroup serverGroup = getServerGroup(BASE_PHASE, serverGroupName);
    yandexCloudFacade.resizeServerGroup(
        credentials, serverGroup.getId(), description.getCapacity().getDesired());
    status(BASE_PHASE, "Done resizing server group '%s'.", serverGroupName);
    return null;
  }
}
