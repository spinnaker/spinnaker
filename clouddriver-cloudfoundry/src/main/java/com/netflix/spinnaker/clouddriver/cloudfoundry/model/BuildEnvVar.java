/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.model;

public enum BuildEnvVar {

  JobName(BuildEnvVar.PREFIX + "BUILD_JOB_NAME"),
  JobNumber(BuildEnvVar.PREFIX + "BUILD_JOB_NUMBER"),
  JobUrl(BuildEnvVar.PREFIX + "BUILD_JOB_URL"),
  Version(BuildEnvVar.PREFIX + "BUILD_VERSION");

  public static final String PREFIX = "__SPINNAKER_";
  public final String envVarName;

  BuildEnvVar(String envVarName) {
    this.envVarName = envVarName;
  }
}
