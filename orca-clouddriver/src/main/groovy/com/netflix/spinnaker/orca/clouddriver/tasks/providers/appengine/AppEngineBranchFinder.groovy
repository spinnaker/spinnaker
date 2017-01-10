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

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage

import java.util.regex.Pattern

class AppEngineBranchFinder {
  static void populateFromStage(Map operation, Stage stage) {
    if (operation.fromTrigger && operation.trigger && stage.execution instanceof Pipeline) {
      Map trigger = (stage.execution as Pipeline).trigger
      def matchConditions = [
        trigger?.source == operation.trigger.source,
        trigger?.project == operation.trigger.project,
        trigger?.slug == operation.trigger.slug,
        trigger?.branch ==~ Pattern.compile(operation.trigger.branch as String)
      ]

      if (matchConditions.every()) {
        operation.branch = trigger.branch
      } else {
        throw new IllegalStateException("No branch found for repository ${operation.repositoryUrl} in trigger context.")
      }
    }
  }
}
