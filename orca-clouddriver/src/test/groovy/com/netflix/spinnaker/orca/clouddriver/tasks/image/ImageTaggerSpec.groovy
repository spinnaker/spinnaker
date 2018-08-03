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

import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

abstract class ImageTaggerSpec<T extends ImageTagger> extends Specification {

  protected abstract T subject()

  @Subject T imageTagger

  def setup() {
    imageTagger = subject()
  }

  @Unroll
  def "should extract imageId from upstream stages"() {
    given:
    def pipeline = new Execution(Execution.ExecutionType.PIPELINE, "orca")
    def stage1 = new Stage(pipeline, "bake", "stage1", stage1Context + [cloudProvider: cloudProvider])
    def stage2 = new Stage(pipeline, "findImageFromTags", "stage2", stage2Context + [cloudProviderType: cloudProvider])
    def stage3 = new Stage(pipeline, "bake", "stage3", [:])

    [stage1, stage2, stage3].each {
      it.refId = it.name
      pipeline.stages << it
    }

    stage3.requisiteStageRefIds = [stage2.refId]
    stage2.requisiteStageRefIds = [stage1.refId]

    expect:
    imageTagger.upstreamImageIds(stage3, consideredStageRefIds, cloudProvider) == expectedImageNames

    where:
    cloudProvider | stage1Context                    | stage2Context    | consideredStageRefIds || expectedImageNames
    'aws'         | [:]                              | [:]              | null                  || []
    'aws'         | [imageId: "xxx"]                 | [:]              | null                  || ["xxx"]
    'aws'         | [imageId: "xxx"]                 | [imageId: "yyy"] | null                  || ["yyy", "xxx"]
    'aws'         | [amiDetails: [[imageId: "xxx"]]] | [:]              | null                  || ["xxx"]
    'aws'         | [amiDetails: [[imageId: "xxx"]]] | [imageId: "yyy"] | null                  || ["yyy", "xxx"]
    'aws'         | [amiDetails: [[imageId: "xxx"]]] | [imageId: "yyy"] | ['stage2']            || ["yyy"]
    'aws'         | [amiDetails: [[imageId: "xxx"]]] | [imageId: "yyy"] | ['stage1']            || ["xxx"]
    'aws'         | [amiDetails: [[imageId: "xxx"]]] | [imageId: "yyy"] | ['stage1', 'stage2']  || ["yyy", "xxx"]
    'aws'         | [amiDetails: [[imageId: "xxx"]]] | [imageId: "yyy"] | ['stage4', 'stage1']  || ["xxx"]
    'aws'         | [amiDetails: [[imageId: "xxx"]]] | [imageId: "yyy"] | ['stage4']            || []
    'gce'         | [:]                              | [:]              | null                  || []
    'gce'         | [imageId: "xxx"]                 | [:]              | null                  || ["xxx"]
    'gce'         | [imageId: "xxx"]                 | [imageId: "yyy"] | null                  || ["yyy", "xxx"]
    'gce'         | [amiDetails: [[imageId: "xxx"]]] | [:]              | null                  || ["xxx"]
    'gce'         | [amiDetails: [[imageId: "xxx"]]] | [imageId: "yyy"] | null                  || ["yyy", "xxx"]
    'gce'         | [imageId: "xxx"]                 | [imageId: "yyy"] | ['stage2']            || ["yyy"]
    'gce'         | [imageId: "xxx"]                 | [imageId: "yyy"] | ['stage1']            || ["xxx"]
    'gce'         | [imageId: "xxx"]                 | [imageId: "yyy"] | ['stage1', 'stage2']  || ["yyy", "xxx"]
  }
}
