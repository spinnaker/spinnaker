/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.cluster

import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractClusterWideClouddriverTask
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractWaitForClusterWideClouddriverTask
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.ShrinkClusterTask
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.WaitForClusterShrinkTask
import org.springframework.stereotype.Component

@Component
class ShrinkClusterStage extends AbstractClusterWideClouddriverOperationStage {
  public static final String PIPELINE_CONFIG_TYPE = "shrinkCluster"

  ShrinkClusterStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  Class<? extends AbstractClusterWideClouddriverTask> getClusterOperationTask() {
    ShrinkClusterTask
  }

  @Override
  Class<? extends AbstractWaitForClusterWideClouddriverTask> getWaitForTask() {
    WaitForClusterShrinkTask
  }
}
