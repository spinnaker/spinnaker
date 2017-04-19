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

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import groovy.util.logging.Slf4j

/**
 * Since pieces of GCP infrastructure (subjects) are often made of many components, read calls nested deep in
 * the object model might fail. This provides facilities to record failed subjects on GCP reads during a caching
 * agent execution.
 */
@Slf4j
trait FailedSubjectChronicler {

  /**
   * A list of subjects the caching agent failed to read from the platform.
   *
   * This should be a list available to the caching agent to manipulate the failed subjects as it wants
   * after the agent runs.
   */
  List<String> failedSubjects
  /**
   * The subject this particular GCP operation is executed on behalf of,
   * e.g. The name of the L7 LB we are attempting to read a healthcheck for.
   */
  String subject

  void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
    log.warn("Failed to read a component of subject ${subject}. The platform error message was:\n ${e.getMessage()}. \nReporting it as 'Failed' to the caching agent. ")
    if (failedSubjects != null) {
      failedSubjects << subject
    }
  }
}
