/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.mahe.pipeline

import groovy.util.logging.Slf4j
import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.mahe.MaheService
import com.netflix.spinnaker.orca.mahe.tasks.CreatePropertiesTask
import com.netflix.spinnaker.orca.mahe.tasks.MonitorPropertiesTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class MonitorCreatePropertyStage extends LinearStage implements CancellableStage {
  public static final String PIPELINE_CONFIG_TYPE = "monitorCreateProperty"

  @Autowired MaheService maheService

  MonitorCreatePropertyStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  CancellableStage.Result cancel(Stage stage) {
    return null
  }

  @Override
  List<Step> buildSteps(Stage stage) {
    [
      buildStep(stage, "createProperties", CreatePropertiesTask),
      buildStep(stage, "monitorProperties", MonitorPropertiesTask),
    ]
  }
}
