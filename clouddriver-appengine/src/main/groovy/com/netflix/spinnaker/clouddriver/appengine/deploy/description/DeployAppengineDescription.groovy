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

package com.netflix.spinnaker.clouddriver.appengine.deploy.description

import com.netflix.spinnaker.clouddriver.appengine.gitClient.AppengineGitCredentialType
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class DeployAppengineDescription extends AbstractAppengineCredentialsDescription implements DeployDescription {
  String accountName
  String application
  String stack
  String freeFormDetails
  String repositoryUrl
  AppengineGitCredentialType gitCredentialType
  String branch
  String applicationDirectoryRoot
  List<String> configFilepaths
  List<String> configFiles
  Boolean promote
  Boolean stopPreviousVersion
}
