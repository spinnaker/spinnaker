/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import spock.lang.Specification

class DeployManifestContextSpec extends Specification {
  ObjectMapper mapper = OrcaObjectMapper.getInstance();

  def "correctly defaults traffic management when it is absent"() {
    given:
    String json = """
{
  "type": "deployManifest"
}
"""

    when:
    DeployManifestContext context = mapper.readValue(json, DeployManifestContext.class)

    then:
    context.getTrafficManagement() != null
    context.getTrafficManagement().isEnabled() == false
    context.getTrafficManagement().getOptions() != null
  }

  def "correctly deserializes a highlander strategy"() {
    given:
    String json = """
{
  "trafficManagement": {
    "enabled": true,
    "options": {
      "enableTraffic": true,
      "namespace": "test",
      "services": [
        "service test-service"
      ],
      "strategy": "highlander"
    }
  },
  "type": "deployManifest"
}
"""

    when:
    DeployManifestContext context = mapper.readValue(json, DeployManifestContext.class)

    then:
    context.getTrafficManagement() != null
    context.getTrafficManagement().isEnabled() == true
    context.getTrafficManagement().getOptions() != null
    context.getTrafficManagement().getOptions().isEnableTraffic() == true
    context.getTrafficManagement().getOptions().getStrategy() == DeployManifestContext.TrafficManagement.ManifestStrategyType.HIGHLANDER
    context.getTrafficManagement().getOptions().getServices() == ["service test-service"]
  }

  def "correctly defaults to strategy NONE and no services"() {
    given:
    String json = """
{
  "trafficManagement": {
    "enabled": true,
    "options": {
      "enableTraffic": true,
      "namespace": "test"
    }
  },
  "type": "deployManifest"
}
"""

    when:
    DeployManifestContext context = mapper.readValue(json, DeployManifestContext.class)

    then:
    context.getTrafficManagement() != null
    context.getTrafficManagement().isEnabled() == true
    context.getTrafficManagement().getOptions() != null
    context.getTrafficManagement().getOptions().isEnableTraffic() == true
    context.getTrafficManagement().getOptions().getStrategy() == DeployManifestContext.TrafficManagement.ManifestStrategyType.NONE
    context.getTrafficManagement().getOptions().getServices() == []
  }

  def "correctly reads disabled flag on traffic management"() {
    given:
    String json = """
{
  "trafficManagement": {
    "enabled": false,
    "options": {
      "enableTraffic": true,
      "namespace": "test"
    }
  },
  "type": "deployManifest"
}
"""

    when:
    DeployManifestContext context = mapper.readValue(json, DeployManifestContext.class)

    then:
    context.getTrafficManagement() != null
    context.getTrafficManagement().isEnabled() == false
  }

  def "correctly defaults skipExpressionEvaluation to false"() {
    given:
    String json = """
{
  "type": "deployManifest"
}
"""

    when:
    DeployManifestContext context = mapper.readValue(json, DeployManifestContext.class)

    then:
    context.isSkipExpressionEvaluation() == false
  }


  def "correctly reads skipExpressionEvaluation"() {
    given:
    String json = """
{
  "skipExpressionEvaluation": true,
  "type": "deployManifest"
}
"""

    when:
    DeployManifestContext context = mapper.readValue(json, DeployManifestContext.class)

    then:
    context.isSkipExpressionEvaluation() == true
  }

  def "correctly deserializes a manifest"() {
    given:
    String json = """
{
  "account": "k8s",
  "cloudProvider": "kubernetes",
  "manifestArtifactAccount": "kubernetes",
  "manifests": [
    {
      "apiVersion": "extensions/v1beta1",
      "kind": "ReplicaSet",
      "metadata": {
        "name": "test",
        "namespace": "docs-site"
      },
      "spec": {
        "replicas": 2,
        "selector": {
          "matchLabels": {
            "app": "test"
          }
        },
        "template": {
          "metadata": {
            "labels": {
              "app": "test"
            }
          },
          "spec": {
            "containers": [
              {
                "image": "gcr.io/spinnaker-marketplace/orca",
                "name": "test",
                "ports": [
                  {
                    "containerPort": 8083
                  }
                ]
              }
            ]
          }
        }
      }
    }
  ],
  "name": "Deploy (Manifest)",
  "skipExpressionEvaluation": false,
  "source": "text",
  "type": "deployManifest"
}
"""

    when:
    DeployManifestContext context = mapper.readValue(json, DeployManifestContext.class)

    then:
    context.manifests.size() == 1
    context.manifests.get(0).get("kind") == "ReplicaSet"
  }

}
