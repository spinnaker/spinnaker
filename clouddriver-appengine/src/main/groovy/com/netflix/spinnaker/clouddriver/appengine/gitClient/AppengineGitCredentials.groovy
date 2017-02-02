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

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.JschConfigSessionFactory
import org.eclipse.jgit.transport.OpenSshConfig
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.util.FS

// Taken from http://www.codeaffine.com/2014/12/09/jgit-authentication/

class AppengineGitCredentials {
  UsernamePasswordCredentialsProvider httpsUsernamePasswordCredentialsProvider
  UsernamePasswordCredentialsProvider httpsOAuthCredentialsProvider
  TransportConfigCallback sshTransportConfigCallback

  AppengineGitCredentials() {}

  AppengineGitCredentials(String gitHttpsUsername,
                          String gitHttpsPassword,
                          String githubOAuthAccessToken,
                          String sshPrivateKeyFilePath,
                          String sshPrivateKeyPassword) {
    setHttpsUsernamePasswordCredentialsProvider(gitHttpsUsername, gitHttpsPassword)
    setHttpsOAuthCredentialsProvider(githubOAuthAccessToken)
    setSshPrivateKeyTransportConfigCallback(sshPrivateKeyFilePath, sshPrivateKeyPassword)
  }

  AppengineGitRepositoryClient buildRepositoryClient(String repositoryUrl,
                                                     String targetDirectory,
                                                     AppengineGitCredentialType credentialType) {
    new AppengineGitRepositoryClient(repositoryUrl, targetDirectory, credentialType, this)
  }

  List<AppengineGitCredentialType> getSupportedCredentialTypes() {
    def supportedTypes = [AppengineGitCredentialType.NONE]

    if (httpsUsernamePasswordCredentialsProvider) {
      supportedTypes << AppengineGitCredentialType.HTTPS_USERNAME_PASSWORD
    }

    if (httpsOAuthCredentialsProvider) {
      supportedTypes << AppengineGitCredentialType.HTTPS_GITHUB_OAUTH_TOKEN
    }

    if (sshTransportConfigCallback) {
      supportedTypes << AppengineGitCredentialType.SSH
    }

    return supportedTypes
  }

  void setHttpsUsernamePasswordCredentialsProvider(String gitHttpsUsername, String gitHttpsPassword) {
    if (gitHttpsUsername && gitHttpsPassword) {
      httpsUsernamePasswordCredentialsProvider = new UsernamePasswordCredentialsProvider(gitHttpsUsername, gitHttpsPassword)
    }
  }

  void setHttpsOAuthCredentialsProvider(String githubOAuthAccessToken) {
    if (githubOAuthAccessToken) {
      httpsOAuthCredentialsProvider = new UsernamePasswordCredentialsProvider(githubOAuthAccessToken, "")
    }
  }

  void setSshPrivateKeyTransportConfigCallback(String sshPrivateKeyFilePath, String sshPrivateKeyPassword) {
    if (sshPrivateKeyPassword && sshPrivateKeyPassword) {
      SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {
        @Override
        protected void configure(OpenSshConfig.Host hc, Session session) { }

        @Override
        protected JSch createDefaultJSch(FS fs) throws JSchException {
          JSch defaultJSch = super.createDefaultJSch(fs)
          defaultJSch.addIdentity(sshPrivateKeyFilePath, sshPrivateKeyPassword)
          return defaultJSch
        }
      }

      sshTransportConfigCallback = new TransportConfigCallback() {
        @Override
        void configure(Transport transport) {
          SshTransport sshTransport = (SshTransport) transport
          sshTransport.setSshSessionFactory(sshSessionFactory)
        }
      }
    }
  }
}
