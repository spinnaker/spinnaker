/*
 * Copyright 2019 Google, LLC
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

package com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.halyard.config.config.v1.StrictObjectMapper;
import java.io.IOException;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.yaml.snakeyaml.Yaml;

@RunWith(JUnitPlatform.class)
final class KubernetesAccountTest {

  @Test
  void testLastOAuthScopeIsKept_capitalA() {
    Yaml yamlParser = new Yaml();
    Object parsedYaml =
        yamlParser.load(
            Joiner.on('\n')
                .join(
                    "oauthScopes: [\"lowercase-a\"]", //
                    "oAuthScopes: [\"uppercase-a\"]"));
    KubernetesAccount account =
        new StrictObjectMapper().convertValue(parsedYaml, KubernetesAccount.class);

    assertThat(account.getOAuthScopes()).containsExactly("uppercase-a");
  }

  @Test
  void testLastOAuthScopeIsKept_lowercaseA() {
    Yaml yamlParser = new Yaml();
    Object parsedYaml =
        yamlParser.load(
            Joiner.on('\n')
                .join(
                    "oAuthScopes: [\"uppercase-a\"]", //
                    "oauthScopes: [\"lowercase-a\"]"));
    KubernetesAccount account =
        new StrictObjectMapper().convertValue(parsedYaml, KubernetesAccount.class);

    assertThat(account.getOAuthScopes()).containsExactly("lowercase-a");
  }

  @Test
  void testLastOAuthServiceAccountIsKept_capitalA() {
    Yaml yamlParser = new Yaml();
    Object parsedYaml =
        yamlParser.load(
            Joiner.on('\n')
                .join(
                    "oauthServiceAccount: \"lowercase-a\"", //
                    "oAuthServiceAccount: \"uppercase-a\""));
    KubernetesAccount account =
        new StrictObjectMapper().convertValue(parsedYaml, KubernetesAccount.class);

    assertThat(account.getOAuthServiceAccount()).isEqualTo("uppercase-a");
  }

  @Test
  void testLastOAuthServiceAccountIsKept_lowercaseA() {
    Yaml yamlParser = new Yaml();
    Object parsedYaml =
        yamlParser.load(
            Joiner.on('\n')
                .join(
                    "oAuthServiceAccount: \"uppercase-a\"", //
                    "oauthServiceAccount: \"lowercase-a\""));
    KubernetesAccount account =
        new StrictObjectMapper().convertValue(parsedYaml, KubernetesAccount.class);

    assertThat(account.getOAuthServiceAccount()).isEqualTo("lowercase-a");
  }

  @Test
  void testOnlyCapitalAIsWritten() throws IOException {
    KubernetesAccount account = new KubernetesAccount();
    account.setOAuthScopes(ImmutableList.of("my-scope"));
    account.setOAuthServiceAccount("my-service-account");

    String result = getYaml(account);

    assertThat(result).contains("oAuthScopes");
    assertThat(result).doesNotContain("oauthScopes");
    assertThat(result).contains("oAuthServiceAccount");
    assertThat(result).doesNotContain("oauthServiceAccount");
  }

  private static String getYaml(KubernetesAccount account) throws IOException {
    StringWriter stringWriter = new StringWriter();
    new StrictObjectMapper().writeValue(stringWriter, account);
    return stringWriter.toString();
  }
}
