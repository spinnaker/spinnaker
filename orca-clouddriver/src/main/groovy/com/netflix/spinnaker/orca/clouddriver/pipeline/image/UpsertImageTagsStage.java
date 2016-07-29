/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.image;

import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.image.ImageForceCacheRefreshTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.image.UpsertImageTagsTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.image.WaitForUpsertedImageTagsTask;
import com.netflix.spinnaker.orca.pipeline.LinearStage;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.batch.core.Step;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class UpsertImageTagsStage extends LinearStage {
  public static final String PIPELINE_CONFIG_TYPE = "upsertImageTags";

  UpsertImageTagsStage() {
    super(PIPELINE_CONFIG_TYPE);
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    return Arrays.asList(
      buildStep(stage, "upsertImageTags", UpsertImageTagsTask.class),
      buildStep(stage, "monitorUpsert", MonitorKatoTask.class),
      buildStep(stage, "forceCacheRefresh", ImageForceCacheRefreshTask.class),
      buildStep(stage, "waitForTaggedImage", WaitForUpsertedImageTagsTask.class)
    );
  }
}
