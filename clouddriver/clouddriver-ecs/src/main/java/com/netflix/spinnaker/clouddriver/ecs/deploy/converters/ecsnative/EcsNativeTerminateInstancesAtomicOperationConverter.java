/*
 * Copyright 2026 Spinnaker contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.deploy.converters.ecsnative;

import com.netflix.spinnaker.clouddriver.ecs.EcsNativeOperation;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.TerminateInstancesDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@EcsNativeOperation(AtomicOperations.TERMINATE_INSTANCES)
@Component("ecsNativeTerminateInstances")
public class EcsNativeTerminateInstancesAtomicOperationConverter
    extends AbstractUnsupportedEcsNativeAtomicOperationConverter<TerminateInstancesDescription> {

  public EcsNativeTerminateInstancesAtomicOperationConverter() {
    super(
        TerminateInstancesDescription.class,
        "Native ECS task termination is not supported because task ownership cannot be verified.");
  }

  /** Maps Spinnaker's {@code instanceIds} onto ECS task ids, matching the classic converter. */
  @Override
  public TerminateInstancesDescription convertDescription(Map input) {
    TerminateInstancesDescription converted = new TerminateInstancesDescription();
    converted.setCredentials(getCredentialsObject(input.get("credentials").toString()));
    converted.setRegion(input.get("region").toString());

    List<String> ecsTaskIds = new ArrayList<>();
    for (Object id : (List) input.get("instanceIds")) {
      ecsTaskIds.add(id.toString());
    }
    converted.setEcsTaskIds(ecsTaskIds);

    return converted;
  }
}
