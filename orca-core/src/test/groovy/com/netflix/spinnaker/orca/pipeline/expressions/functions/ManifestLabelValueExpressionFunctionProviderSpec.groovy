/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipeline.expressions.functions


import com.netflix.spinnaker.orca.pipeline.expressions.SpelHelperFunctionException
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.pipeline.expressions.functions.ManifestLabelValueExpressionFunctionProvider.manifestLabelValue
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class ManifestLabelValueExpressionFunctionProviderSpec extends Specification {

  @Shared
  def deployManifestPipeline = pipeline {
    stage {
      id = "1"
      name = "Deploy ReplicaSet"
      context.putAll(
        "manifests": [
          [
            "kind": "ReplicaSet",
            "spec": [
              "template": [
                "metadata": [
                  "labels": [
                    "my-label-key": "my-label-value",
                    "my-other-label-key": "my-other-label-value"
                  ]
                ]
              ]
            ]
          ]
        ]
      )
      status = SUCCEEDED
      type = "deployManifest"
    }
  }

  @Unroll
  def "manifestLabelValue should resolve label value for manifest of given kind deployed by stage of given name"() {
    expect:
    manifestLabelValue(deployManifestPipeline, "Deploy ReplicaSet", "ReplicaSet", labelKey) == expectedLabelValue

    where:
    labelKey             || expectedLabelValue
    "my-label-key"       || "my-label-value"
    "my-other-label-key" || "my-other-label-value"
  }

  def "manifestLabelValue should raise exception if stage, manifest, or label not found"() {
    when:
    manifestLabelValue(deployManifestPipeline, "Non-existent Stage", "ReplicaSet", "my-label-key")

    then:
    thrown(SpelHelperFunctionException)

    when:
    manifestLabelValue(deployManifestPipeline, "Deploy ReplicaSet", "Deployment", "my-label-key")

    then:
    thrown(SpelHelperFunctionException)

    when:
    manifestLabelValue(deployManifestPipeline, "Deploy ReplicaSet", "ReplicaSet", "non-existent-label")

    then:
    thrown(SpelHelperFunctionException)
  }
}
