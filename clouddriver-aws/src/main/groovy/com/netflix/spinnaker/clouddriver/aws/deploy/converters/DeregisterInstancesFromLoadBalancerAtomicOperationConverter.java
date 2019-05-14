/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.converters;

import com.netflix.spinnaker.clouddriver.aws.AmazonOperation;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AbstractRegionAsgInstanceIdsDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.InstanceLoadBalancerRegistrationDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.InstanceTargetGroupRegistrationDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.DeregisterInstancesFromLoadBalancerAtomicOperation;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.DeregisterInstancesFromTargetGroupAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import java.util.Map;
import org.springframework.stereotype.Component;

@AmazonOperation(AtomicOperations.DEREGISTER_INSTANCES_FROM_LOAD_BALANCER)
@Component("deregisterInstancesFromLoadBalancerDescription")
public class DeregisterInstancesFromLoadBalancerAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {
  private Boolean isClassic(Map input) {
    return !input.containsKey("targetGroupNames");
  }

  @Override
  public AtomicOperation convertOperation(Map input) {
    if (isClassic(input)) {
      return new DeregisterInstancesFromLoadBalancerAtomicOperation(convertDescription(input));
    }
    return new DeregisterInstancesFromTargetGroupAtomicOperation(convertDescription(input));
  }

  @Override
  public AbstractRegionAsgInstanceIdsDescription convertDescription(Map input) {
    AbstractRegionAsgInstanceIdsDescription converted;
    if (isClassic(input)) {
      converted =
          getObjectMapper().convertValue(input, InstanceLoadBalancerRegistrationDescription.class);
    } else {
      converted =
          getObjectMapper().convertValue(input, InstanceTargetGroupRegistrationDescription.class);
    }

    converted.setCredentials(getCredentialsObject((String) input.get("credentials")));
    return converted;
  }
}
