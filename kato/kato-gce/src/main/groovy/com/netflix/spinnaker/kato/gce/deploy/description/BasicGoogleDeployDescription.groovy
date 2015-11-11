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

package com.netflix.spinnaker.kato.gce.deploy.description

import com.netflix.spinnaker.kato.deploy.DeployDescription
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class BasicGoogleDeployDescription extends BaseGoogleInstanceDescription implements DeployDescription {
  String application
  String stack
  String freeFormDetails
  Integer targetSize
  String zone
  List<String> loadBalancers
  Set<String> securityGroups
  Source source = new Source()

  @Canonical
  static class Source {
    // TODO(duftler): Add accountName/credentials to support cloning from one account to another.
    String zone
    String serverGroupName
  }
}
