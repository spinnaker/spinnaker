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

package com.netflix.spinnaker.clouddriver.appengine.gitClient

import groovy.transform.TupleConstructor
import org.eclipse.jgit.api.CloneCommand
import org.eclipse.jgit.api.FetchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.TransportCommand
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

@TupleConstructor
class AppEngineGitRepositoryClient {
  String repositoryUrl
  String targetDirectory
  AppEngineGitCredentialType credentialType
  AppEngineGitCredentials config

  void cloneRepository() {
    CloneCommand command = Git.cloneRepository()
      .setURI(repositoryUrl)
      .setDirectory(new File(targetDirectory))

    attachCredentials(command)

    command.call()
  }

  void fetch() {
    Repository repo = FileRepositoryBuilder.create(new File(targetDirectory, ".git"))
    FetchCommand command = new Git(repo).fetch()

    attachCredentials(command)

    command.call()
  }

  void checkout(String branch) {
    Repository repo = FileRepositoryBuilder.create(new File(targetDirectory, ".git"))
    new Git(repo).checkout().setName("origin/$branch").call()
  }

  private <T extends TransportCommand> void attachCredentials(T command) {
    switch (credentialType) {
      case AppEngineGitCredentialType.HTTPS_USERNAME_PASSWORD:
        command.setCredentialsProvider(config.httpsUsernamePasswordCredentialsProvider)
        break
      case AppEngineGitCredentialType.HTTPS_GITHUB_OAUTH_TOKEN:
        command.setCredentialsProvider(config.httpsOAuthCredentialsProvider)
        break
      case AppEngineGitCredentialType.SSH:
        command.setTransportConfigCallback(config.sshTransportConfigCallback)
        break
      case AppEngineGitCredentialType.NONE:
      default:
        break
    }
  }
}
