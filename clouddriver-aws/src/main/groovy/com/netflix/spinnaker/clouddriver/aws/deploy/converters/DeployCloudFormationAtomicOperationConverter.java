/*
 * Copyright (c) 2019 Schibsted Media Group.
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
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeployCloudFormationDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.DeployCloudFormationAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import org.springframework.stereotype.Component;

import java.util.Map;

@AmazonOperation(AtomicOperations.DEPLOY_CLOUDFORMATION_STACK)
@Component("deployCloudFormationDescription")
public class DeployCloudFormationAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  public AtomicOperation convertOperation(Map input) {
    return new DeployCloudFormationAtomicOperation(convertDescription(input));
  }

  @Override
  public DeployCloudFormationDescription convertDescription(Map input) {
    DeployCloudFormationDescription converted = getObjectMapper()
        .convertValue(input, DeployCloudFormationDescription.class);
    converted.setCredentials(getCredentialsObject((String) input.get("credentials")));
    return converted;
  }
}
