/*
 * Copyright 2026 McIntosh.farm
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
package com.netflix.spinnaker.clouddriver.haproxy.deploy.converters;

import com.netflix.spinnaker.clouddriver.haproxy.HaProxyOperation;
import com.netflix.spinnaker.clouddriver.haproxy.deploy.description.UpsertHaProxyLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.haproxy.deploy.ops.UpsertHaProxyLoadBalancerAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import org.springframework.stereotype.Component;

@HaProxyOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component("haProxyUpsertLoadBalancer")
public class UpsertHaProxyLoadBalancerAtomicOperationConverter
    extends AbstractHaProxyAtomicOperationConverter<UpsertHaProxyLoadBalancerDescription> {

  public UpsertHaProxyLoadBalancerAtomicOperationConverter() {
    super(
        UpsertHaProxyLoadBalancerDescription.class, UpsertHaProxyLoadBalancerAtomicOperation::new);
  }
}
