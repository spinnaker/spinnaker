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


package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws

import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.kato.tasks.DeploymentDetailsAware
import com.netflix.spinnaker.orca.pipeline.model.Stage

class AmazonImageTaggerSupport implements DeploymentDetailsAware {
  static String upstreamImageId(Stage sourceStage) {
    def imageProvidingAncestorStages = sourceStage.ancestors { Stage stage, StageBuilder stageBuilder ->
      return (stage.context.containsKey("imageId") || stage.context.containsKey("amiDetails")) && stage.context.cloudProvider == "aws"
    }

    def imageProvidingStage = imageProvidingAncestorStages[0]?.stage
    if (!imageProvidingStage) {
      return null
    }

    return imageProvidingStage.context.imageId ?: (imageProvidingStage.context.amiDetails as Collection<Map>)[0].imageId
  }
}
