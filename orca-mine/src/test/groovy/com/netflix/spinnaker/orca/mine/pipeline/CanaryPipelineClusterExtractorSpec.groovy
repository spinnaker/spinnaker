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

package com.netflix.spinnaker.orca.mine.pipeline

import spock.lang.Shared
import spock.lang.Specification

class CanaryPipelineClusterExtractorSpec extends Specification {

  @Shared
  CanaryPipelineClusterExtractor extractor = new CanaryPipelineClusterExtractor()

  void 'updateStageClusters replaces existing clusters, removing from queue'() {
    setup:
    def queue = [ [id: 1], [id: 2], [id: 3], [id: 4], [id: 5] ]
    Map stage = [
      "clusterPairs": [
        [ "baseline": [id: 6], "canary": [id: 7] ],
        [ "baseline": [id: 9], "canary": [id: 9] ]
      ]
    ]

    when:
    extractor.updateStageClusters(stage, queue)

    then:
    queue == [ [id: 5] ]
    stage.clusterPairs[0].baseline.id == 1
    stage.clusterPairs[0].canary.id == 2
    stage.clusterPairs[1].baseline.id == 3
    stage.clusterPairs[1].canary.id == 4
  }

  void 'extractClusters returns empty list when no clusters present'() {
    expect:
    extractor.extractClusters([ id: 4 ]) == []
  }

  void 'extractClusters returns clusters in order'() {
    setup:
    Map stage = [
      "clusterPairs": [
        [ "baseline": [id: 6], "canary": [id: 7] ],
        [ "baseline": [id: 8], "canary": [id: 9] ]
      ]
    ]
    expect:
    extractor.extractClusters(stage) == [ [id: 6], [id: 7], [id: 8], [id: 9] ]
  }

}
