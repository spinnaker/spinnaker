/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.validate.v1.providers.dockerRegistry;

import com.amazonaws.util.IOUtils;
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DockerRegistryCatalog;
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryAccount;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

@Component
public class DockerRegistryAccountValidator extends Validator<DockerRegistryAccount> {
  @Override
  public void validate(ConfigProblemSetBuilder p, DockerRegistryAccount n) {
    String resolvedPassword = null;
    String password = n.getPassword();
    String passwordFile = n.getPasswordFile();
    String username = n.getUsername();

    boolean passwordProvided = password != null && !password.isEmpty();
    boolean passwordFileProvided = passwordFile != null && !passwordFile.isEmpty();

    if (passwordProvided && passwordFileProvided) {
      p.addProblem(Severity.ERROR, "You have provided both a password and a password file for your docker registry. You can specify at most one.");
      return;
    }

    try {
      if (passwordProvided) {
        resolvedPassword = password;
      } else if (passwordFileProvided) {
        resolvedPassword = IOUtils.toString(new FileInputStream(passwordFile));

        if (resolvedPassword.isEmpty()) {
          p.addProblem(Severity.WARNING, "The supplied password file is empty.");
        }
      } else {
        resolvedPassword = "";
      }
    } catch (FileNotFoundException e) {
      p.addProblem(Severity.ERROR, "Cannot find provided password file: " + e.getMessage() + ".");
    } catch (IOException e) {
      p.addProblem(Severity.ERROR, "Error reading provided password file: " + e.getMessage() + ".");
    }

    if (resolvedPassword != null && !resolvedPassword.isEmpty()) {
      String message = "Your registry password has %s whitespace; if this is unintentional, authentication may fail.";
      if (Character.isWhitespace(resolvedPassword.charAt(0))) {
        p.addProblem(Severity.WARNING, String.format(message, "leading"));
      }

      if (Character.isWhitespace(resolvedPassword.charAt(resolvedPassword.length() - 1))) {
        p.addProblem(Severity.WARNING, String.format(message, "trailing"));
      }

      if (username == null || username.isEmpty()) {
        p.addProblem(Severity.WARNING, "You have supplied a password but no username.");
      }
    } else {
      if (username != null && !username.isEmpty()) {
        p.addProblem(Severity.WARNING, "You have a supplied a username but no password.");
      }
    }

    DockerRegistryNamedAccountCredentials credentials;
    try {
      credentials = (new DockerRegistryNamedAccountCredentials.Builder())
          .accountName(n.getName())
          .address(n.getAddress())
          .email(n.getEmail())
          .password(n.getPassword())
          .passwordFile(n.getPasswordFile())
          .dockerconfigFile(n.getDockerconfigFile())
          .username(n.getUsername())
          .build();
    } catch (Exception e) {
      p.addProblem(Severity.ERROR, "Failed to instantiate docker credentials for account \"" + n.getName() + "\".");
      return;
    }

    try {
      credentials.getCredentials().getClient().checkV2Availability();
    } catch (Exception e) {
      p.addProblem(Severity.ERROR, "Failed to assert docker registry v2 availability for registry \"" + n.getName() + "\" at address " + n.getAddress() + ": " + e.getMessage() + ".")
        .setRemediation("Make sure that the " + credentials.getV2Endpoint() + " is reachable, and that your credentials are correct.");
    }

    try {
      if (n.getRepositories() == null || n.getRepositories().size() == 0) {
        DockerRegistryCatalog catalog = credentials.getCredentials().getClient().getCatalog();

        if (catalog.getRepositories() == null || catalog.getRepositories().size() == 0) {
          p.addProblem(Severity.ERROR, "Your docker registry has no repositories specified, and the registry's catalog is empty.")
            .setRemediation("Manually specify some repositories for this docker registry to index.");
        }
      }
    } catch (Exception e) {
      p.addProblem(Severity.ERROR, "Unable to connect the registries catalog endpoint: " + e.getMessage() + ".")
        .setRemediation("Manually specify some repositories for this docker registry to index.");
    }
  }
}
