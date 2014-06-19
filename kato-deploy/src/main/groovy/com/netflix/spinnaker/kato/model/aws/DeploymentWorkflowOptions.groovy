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

import com.netflix.spinnaker.kato.model.steps.DeploymentStep
import groovy.transform.Canonical

/**
 * Attributes of the deployment process itself.
 */
@Canonical class DeploymentWorkflowOptions {

    /** Name of the cluster where the deployment is taking place */
    String clusterName

    /** Endpoint where deployment notifications will be sent */
    String notificationDestination

    /** Ordered steps that describe a deployment */
    List<DeploymentStep> steps

}
