/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.googlecommon.config

class GoogleCommonManagedAccount {
  String name
  String environment
  String accountType
  String project
  String jsonPath
  List<String> requiredGroupMembership

  public InputStream getInputStream() {
    if (jsonPath) {
      if (jsonPath.startsWith("classpath:")) {
        return getClass().getResourceAsStream(jsonPath.replace("classpath:", ""))
      } else {
        return new FileInputStream(new File(jsonPath))
      }
    } else {
      return null
    }
  }
}
