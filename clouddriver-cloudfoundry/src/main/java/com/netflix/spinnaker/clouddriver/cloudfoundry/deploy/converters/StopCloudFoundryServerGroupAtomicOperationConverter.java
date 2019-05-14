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
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.StopCloudFoundryServerGroupDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.StopCloudFoundryServerGroupAtomicOperation;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@CloudFoundryOperation(AtomicOperations.DISABLE_SERVER_GROUP)
@Component
public class StopCloudFoundryServerGroupAtomicOperationConverter
    extends AbstractCloudFoundryServerGroupAtomicOperationConverter {
  private final OperationPoller operationPoller;

  public StopCloudFoundryServerGroupAtomicOperationConverter(
      @Qualifier("cloudFoundryOperationPoller") OperationPoller operationPoller) {
    this.operationPoller = operationPoller;
  }

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new StopCloudFoundryServerGroupAtomicOperation(
        operationPoller, convertDescription(input));
  }

  @Override
  public StopCloudFoundryServerGroupDescription convertDescription(Map input) {
    StopCloudFoundryServerGroupDescription converted =
        getObjectMapper().convertValue(input, StopCloudFoundryServerGroupDescription.class);
    converted.setClient(getClient(input));
    converted.setServerGroupId(
        getServerGroupId(
            converted.getServerGroupName(), converted.getRegion(), converted.getClient()));
    return converted;
  }
}
