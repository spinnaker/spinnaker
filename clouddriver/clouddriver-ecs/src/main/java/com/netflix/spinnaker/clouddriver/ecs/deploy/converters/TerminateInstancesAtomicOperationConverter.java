/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.converters;

import com.netflix.spinnaker.clouddriver.ecs.EcsOperation;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.TerminateInstancesDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.ops.TerminateInstancesAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@EcsOperation(AtomicOperations.TERMINATE_INSTANCES)
@Component("ecsTerminateInstances")
public class TerminateInstancesAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  public AtomicOperation convertOperation(Map input) {
    return new TerminateInstancesAtomicOperation(convertDescription(input));
  }

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
