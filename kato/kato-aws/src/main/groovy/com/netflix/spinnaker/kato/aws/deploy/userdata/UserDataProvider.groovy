/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.kato.aws.deploy.userdata

/**
 * Implementations of this interface will provide user data to instances during the deployment process
 *
 *
 */
public interface UserDataProvider {
  /**
   * Returns user data that will be applied to a new instance. The launch configuration will not have been created at
   * this point in the workflow, but the name is provided, as it may be needed when building user data detail.
   *
   * @param asgName
   * @param launchConfigName
   * @param region
   *
   * @return user data string
   */
  String getUserData(String asgName, String launchConfigName, String region, String account, String environment, String accountType)
}
