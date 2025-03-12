/*
 * Copyright 2021 Armory, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.converters;

import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeleteCloudFoundryServiceBindingDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.DeleteCloudFoundryServiceBindingAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@CloudFoundryOperation(AtomicOperations.DELETE_SERVICE_BINDINGS)
@Component
public class DeleteCloudFoundryServiceBindingAtomicOperationConverter
    extends AbstractCloudFoundryServerGroupAtomicOperationConverter {

  @Nullable
  @Override
  public AtomicOperation convertOperation(Map input) {
    return new DeleteCloudFoundryServiceBindingAtomicOperation(convertDescription(input));
  }

  @Override
  public DeleteCloudFoundryServiceBindingDescription convertDescription(Map input) {

    DeleteCloudFoundryServiceBindingDescription description =
        getObjectMapper().convertValue(input, DeleteCloudFoundryServiceBindingDescription.class);
    description.setCredentials(getCredentialsObject(input.get("credentials").toString()));
    description.setClient(getClient(input));
    description.setServerGroupId(
        getServerGroupId(
            description.getServerGroupName(), description.getRegion(), description.getClient()));
    findSpace(description.getRegion(), description.getClient())
        .ifPresentOrElse(
            description::setSpace,
            () -> {
              throw new CloudFoundryApiException("Could not determine CloudFoundry Space.");
            });
    return description;
  }
}
