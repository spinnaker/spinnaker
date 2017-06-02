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
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerClassicDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerV2Description;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.UpsertAmazonLoadBalancerV2AtomicOperation;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonLoadBalancer;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.UpsertAmazonLoadBalancerAtomicOperation;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonLoadBalancerType;
import org.springframework.stereotype.Component;

import java.util.Map;

@AmazonOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component("upsertAmazonLoadBalancerDescription")
class UpsertAmazonLoadBalancerAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  private void sanitizeInput(Map input) {
    if (!input.containsKey("loadBalancerType")) {
      input.put("loadBalancerType", AmazonLoadBalancerType.CLASSIC.toString());
    }
  }

  @Override
  public AtomicOperation convertOperation(Map input) {
    // default to classic load balancer if no type specified
    this.sanitizeInput(input);

    if (input.get("loadBalancerType").equals(AmazonLoadBalancerType.CLASSIC.toString())) {
      return new UpsertAmazonLoadBalancerAtomicOperation(convertDescription(input));
    }
    return new UpsertAmazonLoadBalancerV2AtomicOperation(convertDescription(input));
  }

  @Override
  public UpsertAmazonLoadBalancerDescription convertDescription(Map input) {
    UpsertAmazonLoadBalancerDescription converted;

    this.sanitizeInput(input);
    input.put("loadBalancerType", AmazonLoadBalancerType.getByValue((String)input.get("loadBalancerType")));

    if (input.get("loadBalancerType") == AmazonLoadBalancerType.CLASSIC) {
      converted = getObjectMapper().convertValue(input, UpsertAmazonLoadBalancerClassicDescription.class);
    } else {
      converted = getObjectMapper().convertValue(input, UpsertAmazonLoadBalancerV2Description.class);
    }

    converted.setCredentials(getCredentialsObject((String)input.get("credentials")));
    return converted;
  }
}
