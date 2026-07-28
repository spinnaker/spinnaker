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

import groovy.util.logging.Slf4j
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder
import org.eclipse.jgit.util.FS
import org.apache.sshd.client.config.hosts.HostConfigEntry
import org.apache.sshd.common.session.SessionContext
import java.io.File
import java.nio.file.Path

// Taken from http://www.codeaffine.com/2014/12/09/jgit-authentication/
@Slf4j
class AppengineGitCredentials {
  UsernamePasswordCredentialsProvider httpsUsernamePasswordCredentialsProvider
  UsernamePasswordCredentialsProvider httpsOAuthCredentialsProvider
  TransportConfigCallback sshTransportConfigCallback

  AppengineGitCredentials() {}

  AppengineGitCredentials(String gitHttpsUsername,
                          String gitHttpsPassword,
                          String githubOAuthAccessToken,
                          String sshPrivateKeyFilePath,
                          String sshPrivateKeyPassphrase,
                          String sshKnownHostsFilePath,
                          boolean sshTrustUnknownHosts) {
    setHttpsUsernamePasswordCredentialsProvider(gitHttpsUsername, gitHttpsPassword)
    setHttpsOAuthCredentialsProvider(githubOAuthAccessToken)
    setSshPrivateKeyTransportConfigCallback(sshPrivateKeyFilePath, sshPrivateKeyPassphrase, sshKnownHostsFilePath, sshTrustUnknownHosts)
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

  void setSshPrivateKeyTransportConfigCallback(String sshPrivateKeyFilePath,
                                               String sshPrivateKeyPassphrase,
                                               String sshKnownHostsFilePath,
                                               boolean sshTrustUnknownHosts) {
    if (sshPrivateKeyFilePath && sshPrivateKeyPassphrase) {
      def builder = new SshdSessionFactoryBuilder()

      // Configure the home directory and SSH directory
      File sshDir = FS.DETECTED.userHome() != null ? new File(FS.DETECTED.userHome(), ".ssh") : null
      if (sshDir != null) {
        builder.setHomeDirectory(FS.DETECTED.userHome())
        builder.setSshDirectory(sshDir)
      }

      // Set the private key file
      File privateKeyFile = new File(sshPrivateKeyFilePath)
      builder.setDefaultKeysProvider(file -> {
        return [privateKeyFile.toPath()]
      })

      // Configure known hosts
      if (sshKnownHostsFilePath != null) {
        if (sshTrustUnknownHosts) {
          log.warn("SSH known_hosts file path supplied, ignoring 'sshTrustUnknownHosts' option")
        }
        File knownHostsFile = new File(sshKnownHostsFilePath)
        builder.setServerKeyDatabase((home, session) -> {
          return knownHostsFile
        })
      } else if (sshTrustUnknownHosts) {
        // Accept all host keys without verification
        builder.setServerKeyDatabase((home, session) -> null)
      }

      SshSessionFactory sshSessionFactory = builder.build(null)

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
