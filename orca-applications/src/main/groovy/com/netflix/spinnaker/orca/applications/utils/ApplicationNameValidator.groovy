/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.applications.utils

import com.netflix.spinnaker.orca.front50.model.Application

trait ApplicationNameValidator {

  // NOTE: There's no good way to surface warnings for application names in TaskResult
  // (e.g. if the length of the app name prevents creating load balancers in AWS) so we
  // just validate the characters and name length.
  Map<String, NameConstraint> cloudProviderNameConstraints = [
    'appengine' : new NameConstraint(58, '^[a-z0-9]*$'),
    'aws'       : new NameConstraint(250, '^[a-zA-Z_0-9.]*$'),
    'dcos'      : new NameConstraint(127, '^[a-z0-9]*$'),
    'gce'       : new NameConstraint(63, '^([a-zA-Z][a-zA-Z0-9]*)?$'),
    'kubernetes': new NameConstraint(63, '^([a-zA-Z][a-zA-Z0-9-]*)$'),
    'openstack' : new NameConstraint(250, '^[a-zA-Z_0-9.]*$'),
    'titus'     : new NameConstraint(250, '^[a-zA-Z_0-9.]*$')
  ]

  /**
   * Validate the application name.
   * @param application
   * @return List of validation errors. If non-empty, signals validation failure the caller should exit.
   */
  List<String> validate(Application application) {
    if (application.cloudProviders == null || application.cloudProviders.isEmpty()) {
      return []
    }

    String applicationName = application.name
    def cloudProviders = application.cloudProviders.split(",")
    def validationErrors = cloudProviders
      .findAll { cloudProviderNameConstraints.containsKey(it) }
      .findResults { provider ->
        NameConstraint constraint = cloudProviderNameConstraints[provider]
        if (!applicationName.matches(constraint.nameRegex) || applicationName.length() > constraint.maxLength) {
          return "Invalid application name '$applicationName'." +
            " Must match $constraint.nameRegex for cloud provider '$provider' and be less than $constraint.maxLength characters."
        } else {
          return null
        }
      }

    return validationErrors ?: []
  }
}
