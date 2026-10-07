/*
 * Copyright 2026 McIntosh.farm
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
package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.clouddriver.aws.deploy.description.AsgDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteWarmPoolDescription;
import com.netflix.spinnaker.clouddriver.aws.services.AsgService;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup;

class DeleteWarmPoolAtomicOperationTest {

  AsgService mockAsgService = mock(AsgService.class);
  RegionScopedProviderFactory.RegionScopedProvider mockRegionScopedProvider =
      mock(RegionScopedProviderFactory.RegionScopedProvider.class);
  RegionScopedProviderFactory mockRegionScopedProviderFactory =
      mock(RegionScopedProviderFactory.class);

  @BeforeEach
  void setup() {
    when(mockRegionScopedProvider.getAsgService()).thenReturn(mockAsgService);
    when(mockRegionScopedProviderFactory.forRegion(any(), any()))
        .thenReturn(mockRegionScopedProvider);
    TaskRepository.threadLocalTask.set(new DefaultTask("1"));
  }

  @Test
  void shouldDeleteWarmPoolForEachAsg() {
    DeleteWarmPoolDescription description = new DeleteWarmPoolDescription();
    AsgDescription asg1 = new AsgDescription();
    asg1.setServerGroupName("asg1");
    asg1.setRegion("us-west-1");
    AsgDescription asg2 = new AsgDescription();
    asg2.setServerGroupName("asg1");
    asg2.setRegion("us-east-1");
    description.setAsgs(Arrays.asList(asg1, asg2));
    description.setForceDelete(true);

    when(mockAsgService.getAutoScalingGroup("asg1")).thenReturn(AutoScalingGroup.builder().build());

    DeleteWarmPoolAtomicOperation operation = new DeleteWarmPoolAtomicOperation(description);
    operation.regionScopedProviderFactory = mockRegionScopedProviderFactory;

    operation.operate(Collections.emptyList());

    verify(mockAsgService, times(2)).getAutoScalingGroup("asg1");
    verify(mockAsgService, times(2)).deleteWarmPool("asg1", true);
    verifyNoMoreInteractions(mockAsgService);
  }

  @Test
  void shouldNotDeleteWarmPoolWhenAsgNotFound() {
    DeleteWarmPoolDescription description = new DeleteWarmPoolDescription();
    AsgDescription asg1 = new AsgDescription();
    asg1.setServerGroupName("asg1");
    asg1.setRegion("us-west-1");
    description.setAsgs(Collections.singletonList(asg1));
    description.setForceDelete(false);

    when(mockAsgService.getAutoScalingGroup("asg1")).thenReturn(null);

    DeleteWarmPoolAtomicOperation operation = new DeleteWarmPoolAtomicOperation(description);
    operation.regionScopedProviderFactory = mockRegionScopedProviderFactory;

    operation.operate(Collections.emptyList());

    verify(mockAsgService).getAutoScalingGroup("asg1");
    verifyNoMoreInteractions(mockAsgService);
  }
}
