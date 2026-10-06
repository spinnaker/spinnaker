/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.artifacts.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.web.url.UrlRestrictionsProperties;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AllowedDomainsWarningTest {
  private final Logger logger = (Logger) LoggerFactory.getLogger(BaseHttpArtifactCredentials.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void attachAppender() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(appender);
  }

  @Test
  void warnsWhenATokenIsConfiguredWithoutAllowedDomains() {
    new Credentials(new Account(Optional.of("t"), Optional.empty(), unpinned()));

    assertThat(warnings()).hasSize(1);
    assertThat(warnings().get(0)).contains("test-account").contains("allowedDomains");
  }

  @Test
  void warnsForATokenFileWithoutReadingIt() {
    // the file does not exist; the check must not try to read it
    new Credentials(new Account(Optional.empty(), Optional.of("/does/not/exist"), unpinned()));

    assertThat(warnings()).hasSize(1);
  }

  @Test
  void warnsForBasicAuthWithoutAllowedDomains() {
    new Credentials(new BasicAccount(unpinned()));

    assertThat(warnings()).hasSize(1);
  }

  @Test
  void doesNotWarnWhenAllowedDomainsAreSet() {
    new Credentials(
        new Account(
            Optional.of("t"),
            Optional.empty(),
            UrlRestrictionsProperties.builder()
                .allowedDomains(List.of("gitea\\.example\\.com"))
                .build()));

    assertThat(warnings()).isEmpty();
  }

  @Test
  void doesNotWarnWithoutCredentials() {
    new Credentials(new Account(Optional.empty(), Optional.empty(), unpinned()));

    assertThat(warnings()).isEmpty();
  }

  @Test
  void doesNotWarnWhenTheAccountHasNoUrlRestrictions() {
    new Credentials(new Account(Optional.of("t"), Optional.empty(), null));

    assertThat(warnings()).isEmpty();
  }

  private static UrlRestrictionsProperties unpinned() {
    return new UrlRestrictionsProperties();
  }

  private List<String> warnings() {
    return appender.list.stream()
        .filter(e -> e.getLevel() == Level.WARN)
        .map(ILoggingEvent::getFormattedMessage)
        .collect(Collectors.toList());
  }

  static class Account extends UserInputValidatedArtifactAccount implements TokenAuth {
    private final Optional<String> token;
    private final Optional<String> tokenFile;

    Account(
        Optional<String> token,
        Optional<String> tokenFile,
        UrlRestrictionsProperties restrictions) {
      super("test-account", restrictions, new Permissions.Builder());
      this.token = token;
      this.tokenFile = tokenFile;
    }

    @Override
    public Optional<String> getToken() {
      return token;
    }

    @Override
    public Optional<String> getTokenFile() {
      return tokenFile;
    }
  }

  static class BasicAccount extends UserInputValidatedArtifactAccount implements BasicAuth {
    BasicAccount(UrlRestrictionsProperties restrictions) {
      super("test-account", restrictions, new Permissions.Builder());
    }

    @Override
    public Optional<String> getUsername() {
      return Optional.of("user");
    }

    @Override
    public Optional<String> getPassword() {
      return Optional.of("pw");
    }

    @Override
    public Optional<String> getUsernamePasswordFile() {
      return Optional.empty();
    }
  }

  static class Credentials extends BaseHttpArtifactCredentials<UserInputValidatedArtifactAccount> {
    Credentials(UserInputValidatedArtifactAccount account) {
      super(new OkHttpClient(), account);
    }

    @Override
    public String getName() {
      return getAccount().getName();
    }

    @Override
    public String getType() {
      return "test";
    }

    @Override
    public List<String> getTypes() {
      return List.of();
    }

    @Override
    public InputStream download(Artifact artifact) {
      throw new UnsupportedOperationException();
    }
  }
}
