/*
 * Copyright 2017 Target, Inc.
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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v1.ResourceBuilder
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class ResourceBuilderSpec extends Specification {
    def "adds requests and limits - #description"() {
        given:
        def builder = new ResourceBuilder()
        def deploymentEnvironment = new DeploymentEnvironment()
        deploymentEnvironment.customSizing["echo"] = new HashMap<>(requests: requests, limits: limits)

        when:
        def builtRequirements = builder.buildResourceRequirements("echo", deploymentEnvironment)

        then:
        builtRequirements.requests.get("memory")?.amount == requestsMemory
        builtRequirements.requests.get("cpu")?.amount == requestsCpu
        builtRequirements.limits.get("memory")?.amount == limitsMemory
        builtRequirements.limits.get("cpu")?.amount == limitsCpu

        where:
        description         | requests                                | limits                                    | requestsMemory | requestsCpu | limitsMemory | limitsCpu
        "all"               | new HashMap<>(memory: "1Mi", cpu: "1m") | new HashMap<>(memory: "50Mi", cpu: "50m") | "1Mi"          | "1m"        | "50Mi"       | "50m"
        "only cpu"          | new HashMap<>(cpu: "1m")                | new HashMap<>(cpu: "50m")                 | null           | "1m"        | null         | "50m"
        "only mem"          | new HashMap<>(memory: "1Mi")            | new HashMap<>(memory: "50Mi"            ) | "1Mi"          | null        | "50Mi"       | null
        "only reqs"         | new HashMap<>(memory: "1Mi", cpu: "1m") | null                                      | "1Mi"          | "1m"        | null         | null
        "only limits"       | null                                    | new HashMap<>(memory: "50Mi", cpu: "50m") | null           | null        | "50Mi"       | "50m"
        "integer values"    | new HashMap<>(memory: 1, cpu: 2)        | new HashMap<>(memory: 3, cpu: 4)          | "1"            | "2"         | "3"          | "4"
    }

    def "adds no requests or limits when not specified"() {
        given:
        def builder = new ResourceBuilder()
        def deploymentEnvironment = new DeploymentEnvironment()

        when:
        def builtRequirements = builder.buildResourceRequirements("echo", deploymentEnvironment)

        then:
        builtRequirements == null
    }

    def "noops when given null component"() {
        given:
        def builder = new ResourceBuilder()
        def requests = new HashMap<>(memory: "1Mi", cpu: "1m")
        def limits = new HashMap<>(memory: "50Mi", cpu: "50m")
        def deploymentEnvironment = new DeploymentEnvironment()
        deploymentEnvironment.customSizing["echo"] = new HashMap<>(requests: requests, limits: limits)

        when:
        def builtRequirements = builder.buildResourceRequirements(null, deploymentEnvironment)

        then:
        builtRequirements == null
    }
}
