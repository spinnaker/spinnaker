/*
 * Copyright 2015 Pivotal Inc.
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

package com.netflix.spinnaker.clouddriver.cf.config

class CloudFoundryConstants {

  /**
   * Jenkins-specific metadata
   */
  static String COMMIT_HASH = 'SPINNAKER_BUILD_COMMITHASH'
  static String COMMIT_BRANCH = 'SPINNAKER_BUILD_COMMITBRANCH'
  static String JENKINS_HOST = 'SPINNAKER_BUILD_JENKINS_HOST'
  static String JENKINS_NAME = 'SPINNAKER_BUILD_JENKINS_NAME'
  static String JENKINS_BUILD = 'SPINNAKER_BUILD_JENKINS_BUILD'

  /**
   * General build info
   */
  static String PACKAGE = 'SPINNAKER_BUILD_PACKAGE'

  /**
   * Assigned loadbalancers
   */
  static String LOAD_BALANCERS = 'SPINNAKER_LOAD_BALANCERS'

  /**
   * Image metadata
   *
   */
  static String REPOSITORY = 'SPINNAKER_REPOSITORY'
  static String ARTIFACT = 'SPINNAKER_ARTIFACT'
  static String ACCOUNT = 'SPINNAKER_ACCOUNT'

}
