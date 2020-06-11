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
 *
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.converters;

import static com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations.RUN_JOB;

import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.CloudFoundryRunJobOperationDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.CloudFoundryRunJobOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.Map;
import org.springframework.stereotype.Component;

@CloudFoundryOperation(RUN_JOB)
@Component
public class CloudFoundryRunJobOperationConverter
    extends AbstractCloudFoundryAtomicOperationConverter {

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new CloudFoundryRunJobOperation(convertDescription(input));
  }

  @Override
  public CloudFoundryRunJobOperationDescription convertDescription(Map input) {
    CloudFoundryRunJobOperationDescription converted =
        getObjectMapper().convertValue(input, CloudFoundryRunJobOperationDescription.class);

    CloudFoundryCredentials credentials = getCredentialsObject(input.get("credentials").toString());
    converted.setCredentials(credentials);
    CloudFoundryClient client = credentials.getClient();
    converted.setClient(client);
    String jobName = (String) input.get("jobName");
    String region = (String) input.get("region");
    String serverGroupName = (String) input.get("serverGroupName");

    CloudFoundrySpace space =
        findSpace(region, client)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Unable to find organization and space '" + region + "'."));

    CloudFoundryServerGroup serverGroup =
        client.getApplications().findServerGroupByNameAndSpaceId(serverGroupName, space.getId());

    if (serverGroup == null) {
      throw new IllegalStateException(
          String.format(
              "Can't run job '%s': CloudFoundry application '%s' not found in org/space '%s'",
              jobName, serverGroupName, region));
    }

    converted.setServerGroup(serverGroup);

    return converted;
  }
}
