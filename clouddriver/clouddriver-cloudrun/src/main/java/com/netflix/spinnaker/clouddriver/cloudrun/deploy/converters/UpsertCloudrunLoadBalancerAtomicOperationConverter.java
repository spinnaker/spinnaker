/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.deploy.converters;

import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunOperation;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.UpsertCloudrunLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.ops.UpsertCloudrunLoadBalancerAtomicOperation;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsConverter;
import com.netflix.spinnaker.orchestration.OperationDescription;
import java.util.Map;
import org.springframework.stereotype.Component;

@CloudrunOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component
public class UpsertCloudrunLoadBalancerAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsConverter<CloudrunNamedAccountCredentials> {

  @Override
  public AtomicOperation convertOperation(Map<String, Object> input) {
    return new UpsertCloudrunLoadBalancerAtomicOperation(
        (UpsertCloudrunLoadBalancerDescription) convertDescription(input));
  }

  @Override
  public OperationDescription convertDescription(Map<String, Object> input) {
    // TODO
    return CloudrunAtomicOperationConverterHelper.convertDescription(
        input, this, UpsertCloudrunLoadBalancerDescription.class);
  }
}
