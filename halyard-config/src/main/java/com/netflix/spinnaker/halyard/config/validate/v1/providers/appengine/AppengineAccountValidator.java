/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.config.validate.v1.providers.appengine;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.appengine.AppengineAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AppengineAccountValidator extends Validator<AppengineAccount> {
  @Autowired String halyardVersion;

  @Override
  public void validate(ConfigProblemSetBuilder p, AppengineAccount account) {
    String jsonKey = null;
    String jsonPath = account.getJsonPath();
    String project = account.getProject();
    String knownHostsPath = account.getSshKnownHostsFilePath();
    AppengineNamedAccountCredentials credentials = null;

    boolean hasPassword = account.getGitHttpsPassword() != null;
    boolean hasUsername =
        account.getGitHttpsUsername() != null && !account.getGitHttpsUsername().isEmpty();
    if (hasPassword != hasUsername) {
      if (!hasUsername) {
        p.addProblem(Severity.ERROR, "Git HTTPS password supplied without git HTTPS username.");
      } else {
        p.addProblem(Severity.ERROR, "Git HTTPS username supplied without git HTTPS password.");
      }
    }

    boolean hasSshPrivateKeyPassphrase = account.getSshPrivateKeyPassphrase() != null;
    boolean hasSshPrivateKeyFilePath =
        account.getSshPrivateKeyFilePath() != null && !account.getSshPrivateKeyFilePath().isEmpty();
    if (hasSshPrivateKeyPassphrase != hasSshPrivateKeyFilePath) {
      if (!hasSshPrivateKeyFilePath) {
        p.addProblem(
            Severity.ERROR,
            "SSH private key passphrase supplied without SSH private key filepath.");
      } else {
        p.addProblem(
            Severity.ERROR,
            "SSH private key filepath supplied without SSH private key passphrase.");
      }
    } else if (hasSshPrivateKeyPassphrase && hasSshPrivateKeyFilePath) {
      Path sshPrivateKeyFilePath = validatingFileDecryptPath(account.getSshPrivateKeyFilePath());
      if (sshPrivateKeyFilePath == null) {
        return;
      }
      String sshPrivateKey = validatingFileDecrypt(p, sshPrivateKeyFilePath.toString());
      if (sshPrivateKey == null) {
        return;
      } else if (sshPrivateKey.isEmpty()) {
        p.addProblem(Severity.WARNING, "The supplied SSH private key file is empty.");
      } else {
        try {
          // Assumes that the public key is sitting next to the private key with the extension
          // ".pub".
          KeyPair keyPair = KeyPair.load(new JSch(), sshPrivateKeyFilePath.toString());
          boolean decrypted =
              keyPair.decrypt(secretSessionManager.decrypt(account.getSshPrivateKeyPassphrase()));
          if (!decrypted) {
            p.addProblem(
                Severity.ERROR,
                "Could not unlock SSH public/private keypair with supplied passphrase.");
          }
        } catch (JSchException e) {
          p.addProblem(
              Severity.ERROR,
              "Could not unlock SSH public/private keypair: " + e.getMessage() + ".");
        }
      }
    }

    if (knownHostsPath != null && !knownHostsPath.isEmpty()) {
      String knownHosts = validatingFileDecrypt(p, knownHostsPath);
      if (knownHosts == null) {
        return;
      }
      if (knownHosts.isEmpty()) {
        p.addProblem(Severity.WARNING, "The supplied known_hosts file is empty.");
      }
    }

    if (jsonPath != null && !jsonPath.isEmpty()) {
      jsonKey = validatingFileDecrypt(p, jsonPath);
      if (jsonKey == null) {
        return;
      }
      if (jsonKey.isEmpty()) {
        p.addProblem(Severity.WARNING, "The supplied credentials file is empty.");
      }
    }

    if (jsonPath != null && !jsonPath.isEmpty() && account.isSshTrustUnknownHosts()) {
      p.addProblem(
              Severity.WARNING,
              "You have supplied a known_hosts file path and set the `--ssh-trust-unknown-hosts` flag to true."
                  + " Spinnaker will ignore your `--ssh-trust-unknown-hosts` flag.")
          .setRemediation("Run `--ssh-trust-unknown-hosts false`.");
    }

    if (account.getProject() == null || account.getProject().isEmpty()) {
      p.addProblem(Severity.ERROR, "No appengine project supplied.");
      return;
    }

    try {
      credentials =
          new AppengineNamedAccountCredentials.Builder()
              .jsonKey(jsonKey)
              .project(project)
              .region("halyard")
              .applicationName("halyard " + halyardVersion)
              .build();

    } catch (Exception e) {
      p.addProblem(
          Severity.ERROR, "Error instantiating appengine credentials: " + e.getMessage() + ".");
      return;
    }

    try {
      credentials.getAppengine().apps().get(project).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
        p.addProblem(Severity.ERROR, "No appengine application found for project " + project + ".")
            .setRemediation(
                "Run `gcloud app create --region <region>` to create an appengine application.");
      } else {
        p.addProblem(
            Severity.ERROR, "Failed to connect to appengine Admin API: " + e.getMessage() + ".");
      }
    } catch (Exception e) {
      p.addProblem(
          Severity.ERROR, "Failed to connect to appengine Admin API: " + e.getMessage() + ".");
    }
  }
}
