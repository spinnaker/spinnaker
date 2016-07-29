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

package com.netflix.spinnaker.orca.kato.pipeline

import spock.lang.Shared
import spock.lang.Specification

class ParallelDeployClusterExtractorSpec extends Specification {

  @Shared
  ParallelDeployClusterExtractor extractor = new ParallelDeployClusterExtractor()

  void 'updateStageClusters replaces existing clusters, removing from queue'() {
    setup:
    def queue = [ [id: 1], [id: 2], [id: 3] ]
    Map stage = [ "clusters": [ [id: 4], [id: 5] ] ]

    when:
    extractor.updateStageClusters(stage, queue)

    then:
    stage.clusters == [ [id: 1], [id: 2] ]
    queue == [ [id: 3] ]
  }

  void 'extractClusters returns empty list when no clusters present'() {
    expect:
    extractor.extractClusters([ id: 4 ]) == []
  }

  void 'extractClusters returns clusters'() {
    expect:
    extractor.extractClusters([ clusters: [[id: 1]], otherStuff: [id: 2]]) == [ [id: 1] ]
  }
}
