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

package com.netflix.spinnaker.orca.clouddriver.tasks.image

import com.netflix.spinnaker.orca.kato.tasks.DeploymentDetailsAware
import com.netflix.spinnaker.orca.pipeline.model.Stage

class ImageTaggerSupport implements DeploymentDetailsAware {
  static Collection<String> upstreamImageIds(Stage sourceStage, String cloudProviderType) {
    def imageProvidingAncestorStages = sourceStage.ancestors().findAll { Stage stage ->
      def cloudProvider = stage.context.cloudProvider ?: stage.context.cloudProviderType
      return (stage.context.containsKey("imageId") || stage.context.containsKey("amiDetails")) && cloudProvider == cloudProviderType
    }

    return imageProvidingAncestorStages.findResults {
      return (it.context.imageId ?: (it.context.amiDetails as Collection<Map>)[0]?.imageId) as String
    }
  }
}
