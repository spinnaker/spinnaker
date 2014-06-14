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
package com.netflix.spinnaker.kato.deploy

import com.amazonaws.services.simpleworkflow.flow.annotations.Execute
import com.amazonaws.services.simpleworkflow.flow.annotations.GetState
import com.amazonaws.services.simpleworkflow.flow.annotations.Workflow
import com.amazonaws.services.simpleworkflow.flow.annotations.WorkflowRegistrationOptions
import com.netflix.spinnaker.kato.model.aws.AutoScalingGroupOptions
import com.netflix.spinnaker.kato.model.aws.DeploymentWorkflowOptions
import com.netflix.spinnaker.kato.model.aws.LaunchConfigurationOptions
import com.netflix.spinnaker.kato.model.aws.OperationContext

/**
 * Method contracts and annotations used for the automatic deployment SWF workflow.
 */
@Workflow
@WorkflowRegistrationOptions(defaultExecutionStartToCloseTimeoutSeconds = 604800L)
interface DeploymentWorkflow {

    /**
     * Starts the deployment of a new ASG in an existing cluster.
     *
     * @param operationContext environment where operation will be executed
     * @param deploymentOptions dictate what the deployment will do
     * @param lcOverrides specify changes to the template launch configuration
     * @param asgOverrides specify changes to the template auto scaling group
     */
    @Execute(version = "2.0")
    void deploy(OperationContext operationContext, DeploymentWorkflowOptions deploymentOptions,
                LaunchConfigurationOptions lcOverrides, AutoScalingGroupOptions asgOverrides)

    /**
     * @return current log history of the workflow
     */
    @GetState
    List<String> getLogHistory()
}
