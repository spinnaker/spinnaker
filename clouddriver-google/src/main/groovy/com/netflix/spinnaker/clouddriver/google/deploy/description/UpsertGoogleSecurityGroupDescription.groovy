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

package com.netflix.spinnaker.clouddriver.google.deploy.description

class UpsertGoogleSecurityGroupDescription extends AbstractGoogleCredentialsDescription {
  String securityGroupName
  String description
  String network = "default"
  List<String> sourceRanges
  List<String> sourceTags
  List<Allowed> allowed
  List<String> targetTags

  String accountName

  static class Allowed {
    String ipProtocol
    List<String> portRanges
  }
}
