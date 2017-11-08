/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.appengine

import java.util.regex.Pattern
import com.netflix.spinnaker.orca.pipeline.model.Stage
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE

class AppEngineBranchFinder {
  static String findInStage(Map operation, Stage stage) {
    if (operation.fromTrigger && operation.trigger && stage.execution.type == PIPELINE) {
      Map trigger = stage.execution.trigger

      if (trigger.type == "git") {
        return fromGitTrigger(operation, trigger)
      } else if (trigger.type == "jenkins" || (trigger.type == "manual" && trigger.master && trigger.job)) {
        return fromJenkinsTrigger(operation, trigger)
      } else {
        throw new IllegalArgumentException("Trigger type '${trigger.type}' not supported " +
                                           "for resolving App Engine deployment details dynamically.")
      }
    }
  }

  static String fromGitTrigger(Map operation, Map trigger) {
    def matchConditions = [
      trigger.source == operation.trigger.source,
      trigger.project == operation.trigger.project,
      trigger.slug == operation.trigger.slug,
    ]

    if (operation.trigger.branch) {
      matchConditions << (trigger.branch ==~ Pattern.compile(operation.trigger.branch as String))
    }

    if (matchConditions.every()) {
      return trigger.branch
    } else {
      throwBranchNotFoundException(operation.repositoryUrl)
    }
  }

  /*
  * This method throws an error if it does not resolve exactly one branch from a Jenkin trigger's SCM details.
  * A user can provide a regex to help narrow down the list of branches.
  * */
  static String fromJenkinsTrigger(Map operation, Map trigger) {
    def matchConditions = [
      trigger.master == operation.trigger.master,
      trigger.job == operation.trigger.job
    ]

    if (matchConditions.every()) {
      def branches = trigger.buildInfo?.scm*.branch
      if (operation.trigger.matchBranchOnRegex) {
        def regex = Pattern.compile(operation.trigger.matchBranchOnRegex as String)
        branches = branches.findAll { it ==~ regex }
      }

      if (!branches) {
        throwBranchNotFoundException(operation.repositoryUrl)
      } else if (branches.size() > 1) {
        throw new IllegalStateException("Cannot resolve branch from options: ${branches.join(", ")}.")
      } else {
        return branches[0]
      }
    } else {
      throwBranchNotFoundException(operation.repositoryUrl)
    }
  }

  static void throwBranchNotFoundException(String repositoryUrl) {
    throw new IllegalStateException("No branch found for repository $repositoryUrl in trigger context.")
  }
}
