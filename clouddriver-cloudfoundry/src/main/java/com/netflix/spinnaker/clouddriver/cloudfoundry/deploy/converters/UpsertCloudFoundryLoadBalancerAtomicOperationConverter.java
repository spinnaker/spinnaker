/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.converters;

import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.UpsertCloudFoundryLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.UpsertCloudFoundryLoadBalancerAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import org.springframework.stereotype.Component;

import java.util.Map;

@CloudFoundryOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component
public class UpsertCloudFoundryLoadBalancerAtomicOperationConverter extends AbstractCloudFoundryAtomicOperationConverter {
  @Override
  public AtomicOperation convertOperation(Map input) {
    return new UpsertCloudFoundryLoadBalancerAtomicOperation(convertDescription(input));
  }

  @Override
  public UpsertCloudFoundryLoadBalancerDescription convertDescription(Map input) {
    UpsertCloudFoundryLoadBalancerDescription converted = getObjectMapper().convertValue(input, UpsertCloudFoundryLoadBalancerDescription.class);
    converted.setClient(getClient(input));
    findSpace(converted.getRegion(), converted.getClient())
      .map(converted::setSpace)
      .orElseThrow(() -> new IllegalArgumentException("Unable to find space '" + converted.getRegion() + "'."));
    converted.setDomain(converted.getClient()
      .getDomains().findByName(input.get("domain").toString()));
    return converted;
  }
}
