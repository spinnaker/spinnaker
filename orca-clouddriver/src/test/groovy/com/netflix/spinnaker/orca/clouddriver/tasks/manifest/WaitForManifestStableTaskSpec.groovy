/*
 * Copyright 2019 Cerner Corporation
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
 *
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.model.Manifest
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL

class WaitForManifestStableTaskSpec extends Specification {

  def oortService = Mock(OortService)

  @Subject
  WaitForManifestStableTask task = new WaitForManifestStableTask(
    oortService,
    Stub(ObjectMapper)
  )

  def "task result is SUCCEEDED when a single deployed manifest is stable"() {
    given:
    def stage = createStage([
      "outputs.manifestNamesByNamespace": [
        "my-namespace": ["my-manifest-1"]
      ]
    ])

    when:
    def result = task.execute(stage)

    then:
    1 * oortService.getManifest("my-k8s-account", "my-namespace", "my-manifest-1", false) >> stableManifest("stable", [[type: "warning", message: "my-manifest-1 warning"]])

    result.status == SUCCEEDED
    result.context == createContext([
      stableManifests: [[manifestName: "my-manifest-1", location: "my-namespace"]],
      warnings       : [[type: "warning", message: "my-manifest-1 warning"]]
    ])
  }

  def "task result is TERMINAL when a single deployed manifest is failed"() {
    given:
    def stage = createStage([
      "outputs.manifestNamesByNamespace": [
        "my-namespace": ["my-manifest-1"]
      ]
    ])

    when:
    def result = task.execute(stage)

    then:
    1 * oortService.getManifest("my-k8s-account", "my-namespace", "my-manifest-1", false) >> failedManifest("failed to stabilize")

    result.status == TERMINAL
    result.context == createContext([
      messages       : [
        "'my-manifest-1' in 'my-namespace' for account my-k8s-account: waiting for manifest to stabilize",
        "'my-manifest-1' in 'my-namespace' for account my-k8s-account: failed to stabilize"
      ],
      failedManifests: [[manifestName: "my-manifest-1", location: "my-namespace"]],
      exception      : [
        details: [
          errors: ["'my-manifest-1' in 'my-namespace' for account my-k8s-account: failed to stabilize"]
        ]
      ]
    ])
  }

  def "manifests that are known to be failed or stable are not checked again"() {
    given:
    def stage = createStage([
      "outputs.manifestNamesByNamespace": [
        "my-namespace": ["my-manifest-1", "my-manifest-2", "my-manifest-3", "my-manifest-4"]
      ],
      "stableManifests"                 : [[manifestName: "my-manifest-1", location: "my-namespace"]],
      "failedManifests"                 : [[manifestName: "my-manifest-3", location: "my-namespace"]],
      messages                          : [
        "'my-manifest-2' in 'my-namespace' for account my-k8s-account: waiting for manifest to stabilize",
        "'my-manifest-3' in 'my-namespace' for account my-k8s-account: failed to stabilize"
      ],
      exception                         : [
        details: [
          errors: ["'my-manifest-3' in 'my-namespace' for account my-k8s-account: failed to stabilize"]
        ]
      ],
      warnings                          : [[type: "warning", message: "my-manifest-1 warning"]]
    ])

    when:
    def result = task.execute(stage)

    then:
    1 * oortService.getManifest("my-k8s-account", "my-namespace", "my-manifest-2", false) >> stableManifest()
    1 * oortService.getManifest("my-k8s-account", "my-namespace", "my-manifest-4", false) >> failedManifest("failed to stabilize", [[type: "warning", message: "my-manifest-4 warning"]])
    0 * oortService._

    result.status == TERMINAL
    result.context == createContext([
      stableManifests: [
        [manifestName: "my-manifest-1", location: "my-namespace"],
        [manifestName: "my-manifest-2", location: "my-namespace"]
      ],
      failedManifests: [
        [manifestName: "my-manifest-3", location: "my-namespace"],
        [manifestName: "my-manifest-4", location: "my-namespace"]
      ],
      messages       : [
        "'my-manifest-2' in 'my-namespace' for account my-k8s-account: waiting for manifest to stabilize",
        "'my-manifest-3' in 'my-namespace' for account my-k8s-account: failed to stabilize",
        "'my-manifest-4' in 'my-namespace' for account my-k8s-account: waiting for manifest to stabilize",
        "'my-manifest-4' in 'my-namespace' for account my-k8s-account: failed to stabilize"
      ],
      exception      : [
        details: [
          errors: [
            "'my-manifest-3' in 'my-namespace' for account my-k8s-account: failed to stabilize",
            "'my-manifest-4' in 'my-namespace' for account my-k8s-account: failed to stabilize"
          ]
        ]
      ],
      warnings       : [
        [type: "warning", message: "my-manifest-1 warning"],
        [type: "warning", message: "my-manifest-4 warning"],
      ]
    ])
  }

  def stableManifest(message = "Stable", warnings = null) {
    return Stub(Manifest) {
      getStatus() >> Stub(Manifest.Status) {
        getStable() >> [state: true, message: message]
        getFailed() >> [state: false, message: null]
      }

      if (warnings != null) {
        getWarnings() >> warnings
      }
    }
  }

  def failedManifest(String message, warnings = null) {
    return Stub(Manifest) {
      getStatus() >> Stub(Manifest.Status) {
        getStable() >> [state: false, message: null]
        getFailed() >> [state: true, message: message]
      }


      if (warnings != null) {
        getWarnings() >> warnings
      }
    }
  }

  def createContext(Map extra) {
    return [
      messages       : [],
      stableManifests: [],
      failedManifests: []
    ] + extra
  }

  def createStage(Map extraContext) {
    return new Stage(Stub(Execution), "waitForManifestToStabilize", [
      account: "my-k8s-account",
    ] + extraContext)
  }
}
