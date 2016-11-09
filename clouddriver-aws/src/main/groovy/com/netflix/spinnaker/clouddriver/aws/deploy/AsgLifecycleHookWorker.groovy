/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.PutLifecycleHookRequest
import com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator
import com.netflix.spinnaker.clouddriver.data.task.Task
import groovy.transform.Canonical

import java.util.regex.Pattern

@Canonical
class AsgLifecycleHookWorker {

  private final static REGION_TEMPLATE_PATTERN = Pattern.quote("{{region}}")

  final AmazonClientProvider amazonClientProvider

  final NetflixAmazonCredentials targetCredentials
  final String targetRegion

  IdGenerator idGenerator

  void attach(Task task, List<AmazonAsgLifecycleHook> lifecycleHooks, String targetAsgName) {
    if (lifecycleHooks?.size() == 0) {
      return
    }

    AmazonAutoScaling autoScaling = amazonClientProvider.getAutoScaling(targetCredentials, targetRegion, true)
    lifecycleHooks.each { lifecycleHook ->
      String lifecycleHookName = lifecycleHook.name ?: [targetAsgName, 'lifecycle', idGenerator.nextId()].join('-')
      def request = new PutLifecycleHookRequest(
        autoScalingGroupName: targetAsgName,
        lifecycleHookName: lifecycleHookName,
        roleARN: lifecycleHook.roleARN,
        notificationTargetARN: arnRegionTemplater(lifecycleHook.notificationTargetARN, targetRegion),
        notificationMetadata: lifecycleHook.notificationMetadata,
        lifecycleTransition: lifecycleHook.lifecycleTransition.toString(),
        heartbeatTimeout: lifecycleHook.heartbeatTimeout,
        defaultResult: lifecycleHook.defaultResult.toString()
      )
      autoScaling.putLifecycleHook(request)

      task.updateStatus "AWS_DEPLOY", "Creating lifecycle hook (${request}) on ${targetRegion}/${targetAsgName}"
    }
  }

  private static String arnRegionTemplater(String arnTemplate, String region) {
    arnTemplate.replaceAll(REGION_TEMPLATE_PATTERN, region)
  }
}
