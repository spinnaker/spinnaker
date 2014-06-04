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

import com.amazonaws.services.simpleworkflow.flow.annotations.Activities
import com.amazonaws.services.simpleworkflow.flow.annotations.ActivityRegistrationOptions
import com.netflix.spinnaker.kato.model.aws.AsgDeploymentNames
import com.netflix.spinnaker.kato.model.aws.AutoScalingGroupOptions
import com.netflix.spinnaker.kato.model.aws.LaunchConfigurationOptions
import com.netflix.spinnaker.kato.model.aws.OperationContext

/**
 * Method contracts and annotations used for the automatic deployment SWF workflow actions.
 */
@Activities(version = "2.0")
@ActivityRegistrationOptions(defaultTaskScheduleToStartTimeoutSeconds = -1L,
        defaultTaskStartToCloseTimeoutSeconds = 300L)
interface DeploymentActivities {

    /**
     * Constructs names and identification for previous and next ASGs involved when creating a new ASG for a cluster.
     *
     * @param operationContext environment where operation will be executed
     * @param clusterName where the deployment is taking place
     * @return names and identification for the ASGs involved in the deployment
     */
    AsgDeploymentNames getAsgDeploymentNames(OperationContext operationContext, String clusterName)

    /**
     * Creates the launch configuration for the next ASG in the cluster.
     *
     * @param operationContext environment where operation will be executed
     * @param nextAutoScalingGroup that will use this launch configuration
     * @param inputs for attributes of the new launch configuration
     * @param instancePriceType determines if instance have on demand or spot pricing
     * @return launch configuration attributes
     */
    LaunchConfigurationOptions constructLaunchConfigForNextAsg(OperationContext operationContext,
        AutoScalingGroupOptions nextAutoScalingGroup, LaunchConfigurationOptions inputs)

    /**
     * Creates the launch configuration for the next ASG in the cluster.
     *
     * @param operationContext environment where operation will be executed
     * @param autoScalingGroup that will use this launch configuration
     * @param launchConfiguration attributes for the new launch configuration
     * @return name of the launch configuration
     */
    String createLaunchConfigForNextAsg(OperationContext operationContext, AutoScalingGroupOptions autoScalingGroup,
            LaunchConfigurationOptions launchConfiguration)

    /**
     * Creates the next ASG in the cluster based on the asgOptions but without instances.
     *
     * @param operationContext environment where operation will be executed
     * @param asgOptions attributes for the new ASG
     * @return name of the ASG
     */
    String createNextAsgForClusterWithoutInstances(OperationContext operationContext, AutoScalingGroupOptions asgOptions)

    /**
     * Copies scaling policies from the previous ASG to the next ASG.
     *
     * @param operationContext environment where operation will be executed
     * @param asgDeploymentNames identification for the previous and next ASGs
     * @return number of scaling policies copied
     */
    Integer copyScalingPolicies(OperationContext operationContext, AsgDeploymentNames asgDeploymentNames)

    /**
     * Copies scheduled actions from the previous ASG to the next ASG.
     *
     * @param operationContext environment where operation will be executed
     * @param asgDeploymentNames identification for the previous and next ASGs
     * @return number of scheduled actions copied
     */
    Integer copyScheduledActions(OperationContext operationContext, AsgDeploymentNames asgDeploymentNames)

    /**
     * Changes the instance count and bounds for an ASG.
     *
     * @param operationContext environment where operation will be executed
     * @param asgName of the ASG to modify
     * @param min number of instances allowed
     * @param desired number of instances allowed
     * @param max number of instances
     */
    void resizeAsg(OperationContext operationContext, String asgName, int min, int desired, int max)

    /**
     * Enables scaling behavior for the ASG and traffic to its instances.
     *
     * @param operationContext environment where operation will be executed
     * @param asgName of the ASG to modify
     */
    void enableAsg(OperationContext operationContext, String asgName)

    /**
     * Disables scaling behavior for the ASG and traffic to its instances.
     *
     * @param operationContext environment where operation will be executed
     * @param asgName of the ASG to modify
     */
    void disableAsg(OperationContext operationContext, String asgName)

    /**
     * Deletes an ASG.
     *
     * @param operationContext environment where operation will be executed
     * @param asgName of the ASG to modify
     */
    void deleteAsg(OperationContext operationContext, String asgName)

    /**
     * Runs multiple checks to determine the overall readiness of an ASG.
     *
     * @param operationContext environment where operation will be executed
     * @param asgName of the ASG to modify
     * @param expectedInstances the total number of instances expected in the ASG
     * @return textual description of the reason the ASG is not operational, or an empty String if it is
     */
    String reasonAsgIsNotOperational(OperationContext operationContext, String asgName, int expectedInstances)

    /**
     * Asks if the deployment should proceed and wait for a reply.
     *
     * @param operationContext environment where operation will be executed
     * @param notificationDestination where deployment notifications will be sent
     * @param asgName of the ASG to modify
     * @param operationDescription describes the current operation of the deployment
     * @return indication on whether to proceed with the deployment
     */
    @ActivityRegistrationOptions(defaultTaskScheduleToStartTimeoutSeconds = -1L,
            defaultTaskStartToCloseTimeoutSeconds = 86400L)
    Boolean askIfDeploymentShouldProceed(OperationContext operationContext, String notificationDestination, String asgName, String operationDescription)

    /**
     * Sends a notification about the status of the deployment.
     *
     * @param operationContext environment where operation will be executed
     * @param notificationDestination where deployment notifications will be sent
     * @param asgName of the ASG to modify
     * @param subject of the notification
     * @param rollbackCause textual description of the reason why an ASG is not operational, or null if it is
     */
    void sendNotification(OperationContext operationContext, String notificationDestination, String asgName, String subject, String rollbackCause)

}
