/*
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kato.model.aws

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.Immutable

/**
 * Names and identification of the previous and next ASGs involved when creating a new ASG for a cluster.
 */
@Immutable class AsgDeploymentNames {

    /** Name of the existing auto scaling group used as a template */
    String previousAsgName

    /** Name of the existing launch configuration used as a template */
    String previousLaunchConfigName

    /** Name of the new auto scaling group being created */
    String nextAsgName

    /** Name of the new launch configuration being created */
    String nextLaunchConfigName

    @JsonCreator
    static AsgDeploymentNames of(@JsonProperty('previousAsgName') String previousAsgName,
                                 @JsonProperty('previousLaunchConfigName') String previousLaunchConfigName,
                                 @JsonProperty('nextAsgName') String nextAsgName,
                                 @JsonProperty('nextLaunchConfigName') String nextLaunchConfigName) {
        new AsgDeploymentNames(
                previousAsgName: previousAsgName,
                previousLaunchConfigName: previousLaunchConfigName,
                nextAsgName: nextAsgName,
                nextLaunchConfigName: nextLaunchConfigName
        )
    }

    @JsonIgnore
    /** Name of specific ASG based on the role that it has in the Cluster */
    String getAsgName(AsgRoleInCluster asgRole) {
        if (asgRole == AsgRoleInCluster.Previous) {
            return previousAsgName
        }
        if (asgRole == AsgRoleInCluster.Next) {
            return nextAsgName
        }
        null
    }

}
