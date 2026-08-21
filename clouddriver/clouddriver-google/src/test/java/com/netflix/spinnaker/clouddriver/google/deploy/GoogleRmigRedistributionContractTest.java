/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.services.compute.model.DistributionPolicy;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.api.services.compute.model.InstanceGroupManagerInstanceFlexibilityPolicy;
import com.google.api.services.compute.model.InstanceGroupManagerUpdatePolicy;
import org.junit.jupiter.api.Test;

class GoogleRmigRedistributionContractTest {

  @Test
  void flexPolicyRequiresDisabledRedistribution() {
    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager()
            .setInstanceFlexibilityPolicy(new InstanceGroupManagerInstanceFlexibilityPolicy());

    GoogleRmigRedistributionContract.enforce(instanceGroupManager);

    assertThat(instanceGroupManager.getUpdatePolicy().getInstanceRedistributionType())
        .isEqualTo("NONE");
  }

  @Test
  void nonEvenTargetShapesRequireDisabledRedistribution() {
    for (String targetShape : new String[] {"ANY", "BALANCED", "ANY_SINGLE_ZONE", " any "}) {
      InstanceGroupManager instanceGroupManager =
          new InstanceGroupManager()
              .setDistributionPolicy(new DistributionPolicy().setTargetShape(targetShape));

      GoogleRmigRedistributionContract.enforce(instanceGroupManager);

      assertThat(instanceGroupManager.getUpdatePolicy().getInstanceRedistributionType())
          .isEqualTo("NONE");
    }
  }

  @Test
  void evenTargetShapeWithoutFlexDoesNotSetUpdatePolicy() {
    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager()
            .setDistributionPolicy(new DistributionPolicy().setTargetShape("EVEN"));

    GoogleRmigRedistributionContract.enforce(instanceGroupManager);

    assertThat(instanceGroupManager.getUpdatePolicy()).isNull();
  }

  @Test
  void missingFlexAndTargetShapeDoesNotSetUpdatePolicy() {
    InstanceGroupManager instanceGroupManager = new InstanceGroupManager();

    GoogleRmigRedistributionContract.enforce(instanceGroupManager);

    assertThat(instanceGroupManager.getUpdatePolicy()).isNull();
  }

  @Test
  void preservesExistingUpdatePolicyFields() {
    InstanceGroupManagerUpdatePolicy updatePolicy =
        new InstanceGroupManagerUpdatePolicy().setType("OPPORTUNISTIC");
    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager()
            .setDistributionPolicy(new DistributionPolicy().setTargetShape("BALANCED"))
            .setUpdatePolicy(updatePolicy);

    GoogleRmigRedistributionContract.enforce(instanceGroupManager);

    assertThat(instanceGroupManager.getUpdatePolicy()).isSameAs(updatePolicy);
    assertThat(instanceGroupManager.getUpdatePolicy().getType()).isEqualTo("OPPORTUNISTIC");
    assertThat(instanceGroupManager.getUpdatePolicy().getInstanceRedistributionType())
        .isEqualTo("NONE");
  }

  @Test
  void enforcementIsIdempotent() {
    InstanceGroupManager instanceGroupManager =
        new InstanceGroupManager()
            .setDistributionPolicy(new DistributionPolicy().setTargetShape("ANY"));

    GoogleRmigRedistributionContract.enforce(instanceGroupManager);
    InstanceGroupManagerUpdatePolicy firstUpdatePolicy = instanceGroupManager.getUpdatePolicy();
    GoogleRmigRedistributionContract.enforce(instanceGroupManager);

    assertThat(instanceGroupManager.getUpdatePolicy()).isSameAs(firstUpdatePolicy);
    assertThat(instanceGroupManager.getUpdatePolicy().getInstanceRedistributionType())
        .isEqualTo("NONE");
  }
}
