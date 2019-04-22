/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.converters;

import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeleteCloudFoundryServiceKeyDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.DeleteCloudFoundryServiceKeyAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import org.springframework.stereotype.Component;

import java.util.Map;

@CloudFoundryOperation(AtomicOperations.DELETE_SERVICE_KEY)
@Component
public class DeleteCloudFoundryServiceKeyAtomicOperationConverter extends AbstractCloudFoundryAtomicOperationConverter {
  @Override
  public AtomicOperation convertOperation(Map input) {
    return new DeleteCloudFoundryServiceKeyAtomicOperation(convertDescription(input));
  }

  @Override
  public DeleteCloudFoundryServiceKeyDescription convertDescription(Map input) {
    DeleteCloudFoundryServiceKeyDescription converted = getObjectMapper().convertValue(input, DeleteCloudFoundryServiceKeyDescription.class);
    CloudFoundryClient client = getClient(input);
    converted.setClient(client);
    findSpace(converted.getRegion(), client).ifPresent(converted::setSpace);
    return converted;
  }
}
