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

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops;

import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServiceDescription;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;

class DeployCloudFoundryServiceAtomicOperationTest extends AbstractCloudFoundryAtomicOperationTest {
  DeployCloudFoundryServiceDescription desc = new DeployCloudFoundryServiceDescription();

  @Test
  void deployService() {
    desc.setServiceType("service");
    desc.setClient(client);
    desc.setServiceAttributes(new DeployCloudFoundryServiceDescription.ServiceAttributes()
      .setServiceInstanceName("some-service-name")
      .setService("some-service")
      .setServicePlan("some-service-plan")
    );

    DeployCloudFoundryServiceAtomicOperation op = new DeployCloudFoundryServiceAtomicOperation(desc);

    assertThat(runOperation(op).getHistory())
      .has(status("Creating service instance 'some-service-name' from service some-service and service plan some-service-plan"), atIndex(1))
      .has(status("Created service instance 'some-service-name'"), atIndex(2));
  }

  @Test
  void deployUserProvidedService() {
    desc.setServiceType("userProvided");
    desc.setClient(client);
    desc.setUserProvidedServiceAttributes(new DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes()
      .setServiceInstanceName("some-service-name")
    );

    DeployCloudFoundryServiceAtomicOperation op = new DeployCloudFoundryServiceAtomicOperation(desc);

    assertThat(runOperation(op).getHistory())
      .has(status("Creating user provided service instance 'some-service-name'"), atIndex(1))
      .has(status("Created user provided service instance 'some-service-name'"), atIndex(2));
  }
}
