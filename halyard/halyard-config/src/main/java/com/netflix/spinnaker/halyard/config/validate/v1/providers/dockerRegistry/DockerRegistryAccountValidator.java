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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DefaultDockerOkClientProvider;
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DockerRegistryCatalog;
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials;
import com.netflix.spinnaker.config.DefaultServiceClientProvider;
import com.netflix.spinnaker.config.okhttp3.DefaultOkHttpClientBuilderProvider;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.util.PropertyUtils;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import com.netflix.spinnaker.kork.retrofit.Retrofit2ServiceFactory;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DockerRegistryAccountValidator extends Validator<DockerRegistryAccount> {
  private static final String namePattern = "^[a-z0-9]+([-a-z0-9]*[a-z0-9])?$";

  @Override
  public void validate(ConfigProblemSetBuilder p, DockerRegistryAccount n) {
    if (!Pattern.matches(namePattern, n.getName())) {
      p.addProblem(Severity.ERROR, "Account name must match pattern " + namePattern)
          .setRemediation(
              "It must start and end with a lower-case character or number, and only contain lower-case characters, numbers, or dashes");
    }

    String resolvedPassword = "";
    String password = n.getPassword();
    String passwordCommand = n.getPasswordCommand();
    String passwordFile = n.getPasswordFile();
    String username = n.getUsername();

    boolean passwordProvided = password != null && !password.isEmpty();
    boolean passwordCommandProvided = passwordCommand != null && !passwordCommand.isEmpty();
    boolean passwordFileProvided = passwordFile != null && !passwordFile.isEmpty();

    if (passwordProvided && passwordFileProvided
        || passwordCommandProvided && passwordProvided
        || passwordCommandProvided && passwordFileProvided) {
      p.addProblem(
          Severity.ERROR,
          "You have provided more than one of password, password command, or password file for your docker registry. You can specify at most one.");
      return;
    }

    if (passwordProvided) {
      resolvedPassword = secretSessionManager.decrypt(password);
    } else if (passwordFileProvided) {
      resolvedPassword = validatingFileDecrypt(p, passwordFile);
      if (resolvedPassword == null) {
        return;
      }

      if (resolvedPassword.isEmpty()) {
        p.addProblem(Severity.WARNING, "The supplied password file is empty.");
      }
    } else if (passwordCommandProvided) {
      try {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", passwordCommand);
        Process process = pb.start();
        int errCode = process.waitFor();
        log.debug("Full command is" + pb.command());

        if (errCode != 0) {
          String err = IOUtils.toString(process.getErrorStream(), Charset.defaultCharset());
          log.error("Password command returned a non 0 return code, stderr/stdout was:" + err);
          p.addProblem(
              Severity.WARNING,
              "Password command returned non 0 return code, stderr/stdout was:" + err);
        }

        resolvedPassword =
            IOUtils.toString(process.getInputStream(), Charset.defaultCharset()).trim();

        if (resolvedPassword.length() != 0) {
          log.debug("resolvedPassword is" + resolvedPassword);
        } else {
          p.addProblem(
              Severity.WARNING,
              "Resolved Password was empty, missing dependencies for running password command?");
        }

      } catch (Exception e) {
        p.addProblem(
            Severity.WARNING,
            String.format("Exception encountered when running password command: %s", e));
      }
    }

    if (!resolvedPassword.isEmpty()) {
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
      Path passwordFilePath = validatingFileDecryptPath(n.getPasswordFile());
      credentials =
          (new DockerRegistryNamedAccountCredentials.Builder())
              .accountName(n.getName())
              .address(n.getAddress())
              .email(n.getEmail())
              .password(secretSessionManager.decrypt(n.getPassword()))
              .passwordCommand(n.getPasswordCommand())
              .passwordFile(passwordFilePath != null ? passwordFilePath.toString() : null)
              .dockerconfigFile(n.getDockerconfigFile())
              .username(n.getUsername())
              .clientTimeoutMillis(n.getClientTimeoutMillis())
              .cacheThreads(n.getCacheThreads())
              .paginateSize(n.getPaginateSize())
              .sortTagsByDate(n.getSortTagsByDate())
              .trackDigests(n.getTrackDigests())
              .insecureRegistry(n.getInsecureRegistry())
              .dockerOkClientProvider(new DefaultDockerOkClientProvider())
              .serviceClientProvider(getServiceClientProvider())
              .build();
    } catch (Exception e) {
      p.addProblem(
          Severity.ERROR,
          "Failed to instantiate docker credentials for account \"" + n.getName() + "\".");
      return;
    }

    if (PropertyUtils.anyContainPlaceholder(
        n.getAddress(), n.getUsername(), n.getPassword(), n.getPasswordCommand())) {
      p.addProblem(
          Severity.WARNING,
          "Skipping connection validation because one or more credential contains a placeholder value");
      return;
    }

    ConfigProblemBuilder authFailureProblem = null;
    if (n.getRepositories() == null || n.getRepositories().size() == 0) {
      try {
        DockerRegistryCatalog catalog = credentials.getCredentials().getClient().getCatalog();

        if (catalog.getRepositories() == null || catalog.getRepositories().size() == 0) {
          p.addProblem(
                  Severity.WARNING,
                  "Your docker registry has no repositories specified, and the registry's catalog is empty. Spinnaker will not be able to deploy any images until some are pushed to this registry.")
              .setRemediation(
                  "Manually specify some repositories for this docker registry to index.");
        }
      } catch (Exception e) {
        if (n.getAddress().endsWith("gcr.io")) {
          p.addProblem(
                  Severity.ERROR,
                  "The GCR service requires the Resource Manager API to be enabled for the catalog endpoint to work.")
              .setRemediation(
                  "Visit https://console.developers.google.com/apis/api/cloudresourcemanager.googleapis.com/overview to enable the API.");
        }

        authFailureProblem =
            p.addProblem(
                Severity.ERROR,
                "Unable to connect the registries catalog endpoint: " + e.getMessage() + ".");
      }
    } else {
      // effectively final
      int[] tagCount = new int[1];
      n.getRepositories()
          .forEach(
              r -> {
                try {
                  tagCount[0] +=
                      credentials.getCredentials().getClient().getTags(r).getTags().size();
                } catch (Exception e) {
                  p.addProblem(
                          Severity.ERROR,
                          "Unable to fetch tags from the docker repository: "
                              + r
                              + ", "
                              + e.getMessage())
                      .setRemediation("Can the provided user access this repository?");
                }
              });
      if (tagCount[0] == 0) {
        p.addProblem(
                Severity.WARNING,
                "None of your supplied repositories contain any tags. Spinnaker will not be able to deploy any docker images.")
            .setRemediation("Push some images to your registry.");
      }
    }

    if (authFailureProblem != null && !StringUtils.isEmpty(resolvedPassword)) {
      String message =
          "Your registry password has %s whitespace; if this is unintentional, this may be the cause of failed authentication.";
      if (Character.isWhitespace(resolvedPassword.charAt(0))) {
        authFailureProblem.setRemediation(String.format(message, "leading"));
      }

      char c = resolvedPassword.charAt(resolvedPassword.length() - 1);
      if (Character.isWhitespace(c)) {
        authFailureProblem.setRemediation(String.format(message, "trailing"));

        if (passwordFileProvided && c == '\n')
          authFailureProblem.setRemediation(
              "Your password file has a trailing newline; many text editors append a newline to files they open."
                  + " If you think this is causing authentication issues, you can strip the newline with the command:\n\n"
                  + " tr -d '\\n' < PASSWORD_FILE | tee PASSWORD_FILE");
      }
    }

    try {
      Pattern.compile(n.getRepositoriesRegex());
    } catch (PatternSyntaxException pse) {
      p.addProblem(
              Severity.ERROR,
              "The string provided in repositoriesRegex for account \""
                  + n.getName()
                  + "\" could not compile")
          .setRemediation("The repositoriesRegex must be a valid Regular Expression.");
    }
  }

  // TODO: This is a temporary fix, valid until halyard is upgraded to retrofit2.
  // When halyard is upgraded, an autowired ServiceClientProvider replaces this block
  private ServiceClientProvider getServiceClientProvider() {
    OkHttpClientConfigurationProperties okHttpClientConfigurationProperties =
        new OkHttpClientConfigurationProperties();
    DefaultOkHttpClientBuilderProvider okHttpClientBuilderProvider =
        new DefaultOkHttpClientBuilderProvider(
            new OkHttpClient(), okHttpClientConfigurationProperties);
    OkHttpClientProvider okHttpClientProvider =
        new OkHttpClientProvider(List.of(okHttpClientBuilderProvider));
    Retrofit2ServiceFactory retrofit2ServiceFactory =
        new Retrofit2ServiceFactory(okHttpClientProvider);
    return new DefaultServiceClientProvider(List.of(retrofit2ServiceFactory), new ObjectMapper());
  }
}
