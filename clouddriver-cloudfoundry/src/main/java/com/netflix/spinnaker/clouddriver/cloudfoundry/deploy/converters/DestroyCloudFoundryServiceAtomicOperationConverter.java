/*
 * Copyright 2018 Pivotal, Inc.
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
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DestroyCloudFoundryServiceDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.DestroyCloudFoundryServiceAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import java.util.Map;
import org.springframework.stereotype.Component;

@CloudFoundryOperation(AtomicOperations.DESTROY_SERVICE)
@Component
public class DestroyCloudFoundryServiceAtomicOperationConverter
    extends AbstractCloudFoundryAtomicOperationConverter {

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new DestroyCloudFoundryServiceAtomicOperation(convertDescription(input));
  }

  @Override
  public DestroyCloudFoundryServiceDescription convertDescription(Map input) {
    DestroyCloudFoundryServiceDescription converted =
        getObjectMapper().convertValue(input, DestroyCloudFoundryServiceDescription.class);
    converted.setClient(getClient(input));
    converted.setSpace(
        findSpace(converted.getRegion(), converted.getClient())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Unable to find space '" + converted.getRegion() + "'.")));
    return converted;
  }
}
