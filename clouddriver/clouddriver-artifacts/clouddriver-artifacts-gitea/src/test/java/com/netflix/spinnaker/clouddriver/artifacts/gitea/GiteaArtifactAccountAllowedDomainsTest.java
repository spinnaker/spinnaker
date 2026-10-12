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

package com.netflix.spinnaker.clouddriver.artifacts.gitea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netflix.spinnaker.kork.web.url.UrlRestrictionsProperties;
import java.util.List;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

/** An account that sends credentials has to say which hosts they may be sent to. */
class GiteaArtifactAccountAllowedDomainsTest {
  private static GiteaArtifactAccount.GiteaArtifactAccountBuilder withCredentials() {
    return GiteaArtifactAccount.builder().name("my-account").token("t");
  }

  private static void create(GiteaArtifactAccount account) {
    new GiteaArtifactCredentials(account, new OkHttpClient());
  }

  @Test
  void refusesCredentialsWithoutAllowedDomains() {
    assertThatThrownBy(() -> create(withCredentials().build()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("my-account")
        .hasMessageContaining("allowedDomains");
  }

  @Test
  void refusesCredentialsWhenRestrictionsLeaveAllowedDomainsEmpty() {
    assertThatThrownBy(
            () ->
                create(
                    withCredentials()
                        .urlRestrictions(UrlRestrictionsProperties.builder().build())
                        .build()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void acceptsCredentialsWithAllowedDomains() {
    assertThatCode(
            () ->
                create(
                    withCredentials()
                        .urlRestrictions(
                            UrlRestrictionsProperties.builder()
                                .allowedDomains(List.of("example\\.com"))
                                .build())
                        .build()))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsCredentialsWhenEveryDomainIsAllowedExplicitly() {
    assertThatCode(
            () ->
                create(
                    withCredentials()
                        .urlRestrictions(
                            UrlRestrictionsProperties.builder()
                                .allowedDomains(List.of(".*"))
                                .build())
                        .build()))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsAnAccountWithoutCredentialsAndWithoutAllowedDomains() {
    GiteaArtifactAccount account = GiteaArtifactAccount.builder().name("anonymous").build();

    assertThat(account.hasCredentials()).isFalse();
    assertThatCode(() -> create(account)).doesNotThrowAnyException();
  }
}
