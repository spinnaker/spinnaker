/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.rosco.jobs

import groovy.transform.CompileStatic
import groovy.transform.Immutable

/**
 * A request to bake a new machine image.
 */
@Immutable(copyWith = true)
@CompileStatic
class JobRequest {
  List<String> tokenizedCommand
  List<String> maskedParameters = []
  String jobId
  /** Whether to merge command output and error streams. */
  boolean combineStdOutAndErr = true

  List<String> getMaskedTokenizedCommand() {
    return tokenizedCommand.collect { String masked ->
      String key = masked.split('=').first()
      if (key && key in maskedParameters) {
        masked = "$key=******".toString()
      }
      return masked
    }
  }
}

