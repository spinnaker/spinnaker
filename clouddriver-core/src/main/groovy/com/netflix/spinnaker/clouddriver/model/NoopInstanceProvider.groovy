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

package com.netflix.spinnaker.clouddriver.model

class NoopInstanceProvider implements InstanceProvider<Instance, String> {

  final String cloudProvider = "none"

  @Override
  Instance getInstance(String account, String region, String id) {
    null
  }

  @Override
  String getConsoleOutput(String account, String region, String id) {
    null
  }

}
