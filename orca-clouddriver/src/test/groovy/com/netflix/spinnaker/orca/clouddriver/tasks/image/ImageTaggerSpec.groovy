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

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
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
    def pipeline = new Pipeline("orca")
    def stage1 = new Stage<>(pipeline, "stage1", stage1Context + [cloudProvider: cloudProvider])
    def stage2 = new Stage<>(pipeline, "stage2", stage2Context + [cloudProviderType: cloudProvider])
    def stage3 = new Stage<>(pipeline, "stage3", [:])

    stage3.requisiteStageRefIds = [stage2.id.toString()]
    stage2.requisiteStageRefIds = [stage1.id.toString()]

    [stage1, stage2, stage3].each {
      it.refId = it.id
      pipeline.stages << it
    }

    expect:
    imageTagger.upstreamImageIds(stage3, cloudProvider) == expectedImageNames

    where:
    cloudProvider | stage1Context                    | stage2Context    || expectedImageNames
    'aws'         | [:]                              | [:]              || []
    'aws'         | [imageId: "xxx"]                 | [:]              || ["xxx"]
    'aws'         | [imageId: "xxx"]                 | [imageId: "yyy"] || ["yyy", "xxx"]
    'aws'         | [amiDetails: [[imageId: "xxx"]]] | [:]              || ["xxx"]
    'aws'         | [amiDetails: [[imageId: "xxx"]]] | [imageId: "yyy"] || ["yyy", "xxx"]
    'gce'         | [:]                              | [:]              || []
    'gce'         | [imageId: "xxx"]                 | [:]              || ["xxx"]
    'gce'         | [imageId: "xxx"]                 | [imageId: "yyy"] || ["yyy", "xxx"]
    'gce'         | [amiDetails: [[imageId: "xxx"]]] | [:]              || ["xxx"]
    'gce'         | [amiDetails: [[imageId: "xxx"]]] | [imageId: "yyy"] || ["yyy", "xxx"]
  }
}
