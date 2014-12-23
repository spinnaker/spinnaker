/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.publishing

/**
 * See https://bintray.com/docs/api/#_debian_upload for more details
 */
class BintrayDebPublishingExtension {
  /**
   * Path to debian repo
   */
  String repoName = "ospackages"
  String distribution = "wheezy"
  String component = "main"
  String architecture = "amd64"
  String packageName
  String packageVersion

  /**
   * This is where the deb will be published to at Bintray, not the path within the project
   */
  String packagePath

  /**
   * This is the deb file to actually publish. Defaults to the deb file produced from the named
   * "buildDeb" task, which is applied via the gradle-ospackage-plugin.
   */
  File debFile

  boolean dryRun
}
