/*
 * Copyright 2022 Armory, Apple Inc.
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

package com.netflix.spinnaker.clouddriver.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static uk.org.webcompere.systemstubs.resource.Resources.with;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.netflix.spinnaker.clouddriver.config.AccountDefinitionConfiguration;
import com.netflix.spinnaker.kork.secrets.SecretManager;
import com.netflix.spinnaker.kork.secrets.user.UserSecret;
import com.netflix.spinnaker.kork.secrets.user.UserSecretManager;
import com.netflix.spinnaker.kork.secrets.user.UserSecretReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

@SpringBootTest(classes = AccountDefinitionConfiguration.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class AccountDefinitionSecretManagerTest {

  @MockBean UserSecretManager userSecretManager;

  @MockBean SecretManager secretManager;

  @MockBean AccountSecurityPolicy policy;

  @Autowired AccountDefinitionSecretManager accountDefinitionSecretManager;

  @Test
  void canAccessUserSecret() {
    var userSecret = mock(UserSecret.class);
    given(userSecret.getRoles()).willReturn(List.of("group", "group2"));
    given(userSecret.getSecretString(eq("foo"))).willReturn("bar");
    given(userSecretManager.getUserSecret(any())).willReturn(userSecret);
    given(policy.isAdmin(any())).willReturn(false);
    var username = "user";
    var accountName = "account";
    given(policy.getRoles(username)).willReturn(Set.of("group"));
    given(policy.canUseAccount(username, accountName)).willReturn(true);

    var ref = UserSecretReference.parse("secret://test?k=foo");
    assertThat(accountDefinitionSecretManager.getUserSecretString(ref, accountName))
        .isEqualTo("bar");
    assertThat(accountDefinitionSecretManager.canAccessAccountWithSecrets(username, accountName))
        .isTrue();
  }

  @Test
  void adminHasAccess() {
    var userSecret = mock(UserSecret.class);
    given(userSecret.getRoles()).willReturn(List.of("group", "group2"));
    given(userSecret.getSecretString(eq("foo"))).willReturn("bar");
    given(userSecretManager.getUserSecret(any())).willReturn(userSecret);
    given(policy.isAdmin(any())).willReturn(true);

    var ref = UserSecretReference.parse("secret://test?k=foo");
    var accountName = "cube";
    assertThat(accountDefinitionSecretManager.getUserSecretString(ref, accountName))
        .isEqualTo("bar");
    assertThat(accountDefinitionSecretManager.canAccessAccountWithSecrets("sphere", accountName))
        .isTrue();
  }

  @Test
  void cannotAccessUserSecret() {
    var userSecret = mock(UserSecret.class);
    given(userSecret.getRoles()).willReturn(List.of("group0", "group1"));
    given(userSecret.getSecretString(eq("foo"))).willReturn("bar");
    given(userSecretManager.getUserSecret(any())).willReturn(userSecret);
    given(policy.isAdmin(any())).willReturn(false);
    given(policy.getRoles(any())).willReturn(Set.of("group2", "group3"));

    var accountName = "cube";
    var ref = UserSecretReference.parse("secret://test?k=foo");
    assertThat(accountDefinitionSecretManager.getUserSecretString(ref, accountName))
        .isEqualTo("bar");
    assertThat(accountDefinitionSecretManager.canAccessAccountWithSecrets("sphere", accountName))
        .isFalse();
  }

  @Test
  void canAccessSecretButNotAccount() {
    var userSecret = mock(UserSecret.class);
    given(userSecret.getRoles()).willReturn(List.of("group0", "group1"));
    given(userSecret.getSecretString(eq("foo"))).willReturn("bar");
    given(userSecretManager.getUserSecret(any())).willReturn(userSecret);
    given(policy.isAdmin(any())).willReturn(false);
    given(policy.getRoles(any())).willReturn(Set.of("group0", "group1"));
    given(policy.canUseAccount(any(), any())).willReturn(false);

    var accountName = "cube";
    var ref = UserSecretReference.parse("secret://test?k=foo");
    assertThat(accountDefinitionSecretManager.getUserSecretString(ref, accountName))
        .isEqualTo("bar");
    assertThat(accountDefinitionSecretManager.canAccessAccountWithSecrets("sphere", accountName))
        .isFalse();
  }

  @Test
  void canAccessAccountWhenUserAndAccountHaveNoPermissions() {
    given(policy.isAdmin(any())).willReturn(false);
    var username = "user";
    var accountName = "account";
    given(policy.getRoles(username)).willReturn(Set.of());
    // also assuming that this user can user the account in general
    given(policy.canUseAccount(username, accountName)).willReturn(true);

    assertThat(accountDefinitionSecretManager.canAccessAccountWithSecrets(username, accountName))
        .isTrue();
  }

  @Test
  void testVersionCompatibilityForGoogleSecretManager() throws Exception {

    String credentials =
        "{\n"
            + "  \"type\": \"service_account\",\n"
            + "  \"project_id\": \"my-test-project\",\n"
            + "  \"private_key_id\": \"aaaaaaaaaaaaaaaaaaa\",\n"
            + "  \"private_key\": \"-----BEGIN PRIVATE KEY-----\\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDmKDSt86RQ59Zx\\njezHgVF4IWCwbE6QzdnJGumPnpIvTS5/575A9grY5WB5s4H4FnrrmLVJe5T0mxc5\\nrJ4v9JKyHeTQn+OdMV0zwhJczIN+raROZ9GJyxgdYysiyRR1ajkd8aX+aiU5A4r9\\nFcIIcbLkrlltKfSV3I6tiE0oZja2sj+OE4+3b85NMiBUeusLH7GRglRDddnCAysY\\nvk2tIsYjI9m+f6r731rrmAU1SA5sx7pbRMJdWPVxCSrJy0F+AH/Hn1rTD+ga1m4+\\ngLuBbjZLkfFeg7xsqWKTImaMxKDNpiaNiUaBrBXS1u6IqfjDMakFU9tm5sbGuauG\\nXdIqYo0DAgMBAAECggEABPTPsZriEN+O0ovKXtkUXo0yQYpYV/qLp2Hqrq35zh/2\\neBWolxbu6kQ1doypjosGMAWhV2KpNTbglXYRdma1zZuI/mWH2sfVDuGUHXOszz3a\\noHLQfdjZFstSAsh1JgdY3iHo8uVPrBfwVpcXdX2xUW0s8Tj3X4GY5vhc8cysF/VA\\nDEAlhsxWqanZTslQrtGpuV3q0VoHaFVmf5XcxHm7IOo7UFbbmiOh2WLi22Uv7NxA\\nemqWEGzgU/j/aYoLA2YxmXmuebMTkAwuYfRYEJQ/m2/P/dnPUWRWKp6goaii+B1o\\n/SgfVxet9yM6ChUi7DVc4uRMjCDP/GkY6c26jsrLpQKBgQD4eCMFbXRb1JZGaH6D\\nIqrk+/To9wqxKOI1J3ti2mFLPG9+Pf4Erey4GkmXtFOjDxHfNq9Q4gNi2MDJDgYe\\nglnt4foPG3lqaus3cY7TcVv3cBRfMp1OX1v0Q5MmMyrcVKm9kLueFluLvSY8J8Bw\\nSF37YmWFmdANbxs2tybyTtQtLwKBgQDtIfwwlP6cSCgXaFzuQnkptLn0M5PQ8A0F\\nErxLwLnAgvIP5xI7AtOjVx0Uu7x5XGiTWnIhLU9ODkGQatcxocTmKOQ63oFiOEze\\n33XZKo1gq/ZDx7LGWpDmBR3xqgk/HwlzmuKlR0SqcS+xuU84mrNDqB8ayf6GCkao\\nYRnlE/WwbQKBgFxQXkqc8PdRU4fTOPXFwpKS3dpUNp+9ndW71obStgU67f2MUL0y\\nVVnNQnxfnhdd+Pjim15EqpdmCrJoSHO7YGgWZk6ImaKlGMEfqr36Rv32oUsBRhqh\\nKUvmc1xk9E6qEeqBRIOmsNqJKxR8fG37JRfJ5gguLnNfTVAV2h16ljA3AoGBAN+z\\nnNYz6JGEHJYgdPKrsOOgQ4BVG9ASdSXhC9Mmx9UNcs9/vBoBS6geqSeDB4UxoNHJ\\nlDsqJFNNbZqQv9tpcXdzAgNrHoGK/TGPevxYgTC+aL5+aG9oxqLIFvyA3OI4JFFz\\nvvYOan+j8Utmto5+mjhsJJPAFKVcklWL7MLHdpJtAoGAQHnyuT12Dzi3SeOMavhD\\nheJ6ant6pzH7bfifWcz558IfLtnxtYFR4azKW6n8SUnVCyybPMwVt4pxFZ6as0zK\\nKaplMuWadDqTSE56LJmG854fxQWq4/Z0qnYr9Yq7UHSRtNT7xOxyfiJkTVYqAT6q\\nYcPfY81+aw4wdjnWrDw5dSk=\\n-----END PRIVATE KEY-----\\n\",\n"
            + "  \"client_email\": \"dummy@my-test-project.iam.gserviceaccount.com\",\n"
            + "  \"client_id\": \"1111111111111111111\",\n"
            + "  \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",\n"
            + "  \"token_uri\": \"https://oauth2.googleapis.com/token\",\n"
            + "  \"auth_provider_x509_cert_url\": \"https://www.googleapis.com/oauth2/v1/certs\",\n"
            + "  \"client_x509_cert_url\": \"https://www.googleapis.com/robot/v1/metadata/x509/dummy%40my-test-project.iam.gserviceaccount.com\"\n"
            + "}";
    String credentialsPath = writeToFile(credentials, "credentials.json");
    with(new EnvironmentVariables().set("GOOGLE_APPLICATION_CREDENTIALS", credentialsPath))
        .execute(() -> assertDoesNotThrow(() -> SecretManagerServiceClient.create()));
  }

  private String writeToFile(String content, String fileName) throws IOException {
    Path filePath = Paths.get(System.getProperty("java.io.tmpdir"), fileName);
    Files.write(filePath, content.getBytes());
    return filePath.toString();
  }
}
