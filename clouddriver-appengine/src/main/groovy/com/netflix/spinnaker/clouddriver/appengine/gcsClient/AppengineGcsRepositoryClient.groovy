/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.gcsClient

import com.netflix.spinnaker.clouddriver.appengine.AppengineJobExecutor
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineRepositoryClient

import groovy.transform.TupleConstructor

@TupleConstructor
class AppengineGcsRepositoryClient implements AppengineRepositoryClient {
  String repositoryUrl
  String targetDirectory
  String applicationDirectoryRoot
  AppengineJobExecutor jobExecutor

  void initializeLocalDirectory() {
    rsync()
  }

  void updateLocalDirectoryWithVersion(String version) {
    rsync()
  }

  void rsync() {
    def dest = targetDirectory + '/' + applicationDirectoryRoot
    new File(dest).mkdirs()  // ensure target root exists

    def command  = ["gsutil", "-m", "rsync", "-d", "-r",
                    repositoryUrl + '/' + applicationDirectoryRoot,
                    dest]
                    
    jobExecutor.runCommand(command)
  }
}
