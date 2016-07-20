/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.domain

import java.util.regex.Matcher
import java.util.regex.Pattern

trait LoadBalancerResolver {

  final String nameRegex = "(\\w+)-(\\w+)-(\\w+)"
  final Pattern namePattern = Pattern.compile(nameRegex)
  final String descriptionRegex = ".*internal_port=([0-9]+).*"
  final Pattern descriptionPattern = Pattern.compile(descriptionRegex)

  String getBaseName(final String derivedName) {
    String result = null
    if (derivedName) {
      Matcher matcher = namePattern.matcher(derivedName)

      if (matcher.matches() && matcher.groupCount() == 3) {
        result = matcher.group(1)
      }
    }
    result
  }

  int getInternalPort(final String description) {
    int result = -1
    if (description) {
      Matcher matcher = descriptionPattern.matcher(description)
      if (matcher.matches() && matcher.groupCount() == 1) {
        result = matcher.group(1).toInteger()
      }
    }
    result
  }

}
