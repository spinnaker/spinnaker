/*
 * Copyright 2017 Armory, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.artifacts.github;

import com.google.common.base.Strings;
import com.netflix.spinnaker.clouddriver.artifacts.config.BasicAuth;
import com.netflix.spinnaker.clouddriver.artifacts.config.HttpUrlRestrictions;
import com.netflix.spinnaker.clouddriver.artifacts.config.TokenAuth;
import com.netflix.spinnaker.clouddriver.artifacts.config.UserInputValidatedArtifactAccount;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
<<<<<<< HEAD
=======
import com.netflix.spinnaker.kork.github.GitHubAppCredentials;
import com.netflix.spinnaker.kork.web.url.UrlRestrictionsProperties;
import java.util.List;
>>>>>>> 4d2819f (fix(clouddriver)!: require allowedDomains for artifact accounts that send credentials (#8171))
import java.util.Optional;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@NonnullByDefault
@Value
public class GitHubArtifactAccount extends UserInputValidatedArtifactAccount
    implements BasicAuth, TokenAuth {
  /**
   * The hosts of github.com that an account fetches from: the reference itself, the contents API,
   * the raw content URLs that the contents API returns, and the hosts those redirect to (each
   * redirect hop is validated against the allowed domains too). An account with credentials and no
   * {@code urlRestrictions.allowedDomains} is limited to these, so a GitHub Enterprise account has
   * to configure its own hosts.
   */
  public static final List<String> DEFAULT_ALLOWED_DOMAINS =
      List.of(
          "github\\.com",
          "api\\.github\\.com",
          "codeload\\.github\\.com",
          "raw\\.githubusercontent\\.com",
          "media\\.githubusercontent\\.com",
          "objects\\.githubusercontent\\.com");

  /*
   One of the following are required for auth:
    - username and password
    - usernamePasswordFile : path to file containing "username:password"
    - token
    - tokenFile : path to file containing token
  */
  private final Optional<String> username;
  private final Optional<String> password;
  private final Optional<String> usernamePasswordFile;
  private final Optional<String> token;
  private final Optional<String> tokenFile;
  private final String githubAPIVersion;
  private final boolean useContentAPI;

  @Builder
  @ConstructorBinding
  @ParametersAreNullableByDefault
  GitHubArtifactAccount(
      String name,
      String username,
      String password,
      String usernamePasswordFile,
      String token,
      String tokenFile,
      String githubAPIVersion,
      boolean useContentAPI,
      HttpUrlRestrictions urlRestrictions) {
    super(
        Strings.nullToEmpty(name),
<<<<<<< HEAD
        Optional.ofNullable(urlRestrictions).orElse(HttpUrlRestrictions.builder().build()));
=======
        orDefault(
            urlRestrictions,
            hasCredentials(username, password, usernamePasswordFile, token, tokenFile, githubApp),
            DEFAULT_ALLOWED_DOMAINS),
        Optional.ofNullable(permissions).orElseGet(Permissions.Builder::new));
>>>>>>> 4d2819f (fix(clouddriver)!: require allowedDomains for artifact accounts that send credentials (#8171))
    this.username = Optional.ofNullable(Strings.emptyToNull(username));
    this.password = Optional.ofNullable(Strings.emptyToNull(password));
    this.usernamePasswordFile = Optional.ofNullable(Strings.emptyToNull(usernamePasswordFile));
    this.token = Optional.ofNullable(Strings.emptyToNull(token));
    this.tokenFile = Optional.ofNullable(Strings.emptyToNull(tokenFile));
    this.githubAPIVersion = StringUtils.defaultString(githubAPIVersion, "v3");
    this.useContentAPI = useContentAPI;
  }

  private static boolean hasCredentials(
      String username,
      String password,
      String usernamePasswordFile,
      String token,
      String tokenFile,
      GitHubAppCredentials githubApp) {
    return githubApp != null
        || !Strings.isNullOrEmpty(usernamePasswordFile)
        || (!Strings.isNullOrEmpty(username) && !Strings.isNullOrEmpty(password))
        || !Strings.isNullOrEmpty(token)
        || !Strings.isNullOrEmpty(tokenFile);
  }

  @Override
  public boolean hasCredentials() {
    return githubApp.isPresent() || super.hasCredentials();
  }
}
