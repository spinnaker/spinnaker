/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.tasks.scalingprocess

import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReference
import org.springframework.stereotype.Component

@Component
class ResumeScalingProcessTask extends AbstractScalingProcessTask {
  String type = "resumeAsgProcessesDescription"

  @Override
  List<String> filterProcesses(TargetReference targetReference, List<String> processes) {
    if (!processes) {
      return []
    }

    def targetAsgConfiguration = targetReference.asg.asg as Map<String, Object>
    if (targetAsgConfiguration.suspendedProcesses) {
      def suspendedProcesses = targetAsgConfiguration.suspendedProcesses*.processName as List<String>
      return suspendedProcesses.intersect(processes) ?: []
    }

    return []
  }
}
