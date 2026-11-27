/*
 * Copyright 2026 spinnaker.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.kork.google.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.auth.oauth2.UserCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class GoogleCredentialsFactoryTest {
  private static final String TARGET = "reader@identity-project.iam.gserviceaccount.com";
  private static final List<String> SCOPES =
      List.of("https://www.googleapis.com/auth/devstorage.read_only");
  // Synthetic authorized-user data; never use real credentials in fixtures.
  private static final String FILE_CONTENTS =
      "{\"type\":\"authorized_user\",\"client_id\":\"test-client\","
          + "\"client_secret\":\"test-only\",\"refresh_token\":\"test-only\"}";

  @Test
  void scopesAdcWithoutOpeningAFile() throws IOException {
    GoogleCredentials source = mock(GoogleCredentials.class);
    GoogleCredentials scoped = mock(GoogleCredentials.class);
    when(source.createScoped(SCOPES)).thenReturn(scoped);
    GoogleCredentialsFactory factory =
        new GoogleCredentialsFactory(
            () -> source,
            path -> {
              throw new AssertionError("must not open a file for ADC");
            });

    assertThat(factory.create(new GoogleAuthProperties(), SCOPES)).isSameAs(scoped);
    verify(source).createScoped(SCOPES);
  }

  @Test
  void loadsAndClosesFileWithoutDiscoveringAdc() throws IOException {
    TrackingStream stream = new TrackingStream(FILE_CONTENTS);
    GoogleCredentialsFactory factory = fileFactory(stream);
    GoogleAuthProperties auth = new GoogleAuthProperties();
    auth.setCredentialsFile("test-credentials.json");

    assertThat(factory.create(auth, SCOPES)).isInstanceOf(UserCredentials.class);
    assertThat(stream.closed).isTrue();
  }

  @Test
  void fileCredentialsCanBeTheImpersonationSource() throws IOException {
    GoogleAuthProperties auth = new GoogleAuthProperties();
    auth.setCredentialsFile("test-credentials.json");
    auth.setImpersonateServiceAccount(TARGET);
    ImpersonatedCredentials result =
        (ImpersonatedCredentials)
            fileFactory(new TrackingStream(FILE_CONTENTS)).create(auth, SCOPES);

    assertThat(result.getSourceCredentials()).isInstanceOf(UserCredentials.class);
    assertThat(result.getAccount()).isEqualTo(TARGET);
    assertThat(result.toBuilder().getScopes()).containsExactlyElementsOf(SCOPES);
  }

  @Test
  void invalidFileClosesStreamAndDoesNotFallBack() {
    TrackingStream stream = new TrackingStream("{}");
    GoogleAuthProperties auth = new GoogleAuthProperties();
    auth.setCredentialsFile("test-credentials.json");

    assertThatThrownBy(() -> fileFactory(stream).create(auth, SCOPES))
        .isInstanceOf(IOException.class);
    assertThat(stream.closed).isTrue();
  }

  @Test
  void missingFileDoesNotFallBack() {
    GoogleAuthProperties auth = new GoogleAuthProperties();
    auth.setCredentialsFile("missing.json");
    GoogleCredentialsFactory factory =
        new GoogleCredentialsFactory(
            () -> {
              throw new AssertionError("must not discover ADC after a file failure");
            },
            path -> {
              throw new IOException("file unavailable");
            });

    assertThatThrownBy(() -> factory.create(auth, SCOPES))
        .isInstanceOf(IOException.class)
        .hasMessage("file unavailable");
  }

  @Test
  void missingAdcDoesNotFallBack() {
    GoogleCredentialsFactory factory =
        new GoogleCredentialsFactory(
            () -> {
              throw new IOException("ADC unavailable");
            },
            path -> {
              throw new AssertionError("must not try a file");
            });

    assertThatThrownBy(() -> factory.create(new GoogleAuthProperties(), SCOPES))
        .isInstanceOf(IOException.class)
        .hasMessage("ADC unavailable");
  }

  @Test
  void scopesImpersonationSourceForIamAndTargetForTheConsumer() throws IOException {
    GoogleCredentials source = mock(GoogleCredentials.class);
    GoogleCredentials scopedSource = mock(GoogleCredentials.class);
    when(source.createScoped(List.of("https://www.googleapis.com/auth/cloud-platform")))
        .thenReturn(scopedSource);
    GoogleAuthProperties auth = new GoogleAuthProperties();
    auth.setImpersonateServiceAccount(TARGET);
    GoogleCredentialsFactory factory = new GoogleCredentialsFactory(() -> source, path -> null);

    ImpersonatedCredentials result = (ImpersonatedCredentials) factory.create(auth, SCOPES);
    assertThat(result.getSourceCredentials()).isSameAs(scopedSource);
    assertThat(result.toBuilder().getScopes()).containsExactlyElementsOf(SCOPES);
    assertThat(result.toBuilder().getLifetime()).isEqualTo(3600);
  }

  @Test
  void impersonationRefreshCallsIamWithTheTargetScopesAndLifetime() throws IOException {
    MockLowLevelHttpRequest request = new MockLowLevelHttpRequest();
    request.setResponse(
        new MockLowLevelHttpResponse()
            .setContentType("application/json")
            .setContent(
                "{\"accessToken\":\"test-target-token\",\"expireTime\":\""
                    + Instant.now().plusSeconds(3600)
                    + "\"}"));
    MockHttpTransport transport =
        new MockHttpTransport.Builder().setLowLevelHttpRequest(request).build();
    ImpersonatedCredentials result = impersonatedCredentials();
    result.setTransportFactory(() -> transport);

    assertThat(result.getRequestMetadata()).containsKey("Authorization");
    assertThat(request.getContentAsString()).contains(SCOPES.get(0), "3600s");
    assertThat(result.getAccessToken().getExpirationTime()).isNotNull();
  }

  @Test
  void permissionDeniedNeverReturnsTheSourceToken() throws IOException {
    MockHttpTransport transport =
        new MockHttpTransport.Builder()
            .setLowLevelHttpResponse(
                new MockLowLevelHttpResponse()
                    .setStatusCode(403)
                    .setContentType("application/json")
                    .setContent("{\"error\":\"permission denied\"}"))
            .build();
    ImpersonatedCredentials result = impersonatedCredentials();
    result.setTransportFactory(() -> transport);

    assertThatThrownBy(result::getRequestMetadata).isInstanceOf(IOException.class);
    assertThat(result.getAccessToken()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t"})
  void rejectsBlankExplicitFileBeforeAdcDiscovery(String value) {
    GoogleAuthProperties auth = new GoogleAuthProperties();
    auth.setCredentialsFile(value);
    assertThatThrownBy(() -> new GoogleCredentialsFactory().create(auth, SCOPES))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("auth.credentialsFile");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        " ",
        "reader",
        "reader@example.com",
        " reader@identity.iam.gserviceaccount.com"
      })
  void rejectsInvalidTargetBeforeAdcDiscovery(String value) {
    GoogleAuthProperties auth = new GoogleAuthProperties();
    auth.setImpersonateServiceAccount(value);
    assertThatThrownBy(() -> new GoogleCredentialsFactory().create(auth, SCOPES))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("auth.impersonateServiceAccount");
  }

  @Test
  void supportsDefaultComputeServiceAccountTargets() throws IOException {
    GoogleAuthProperties auth =
        GoogleAuthProperties.builder()
            .impersonateServiceAccount("123456789-compute@developer.gserviceaccount.com")
            .build();
    GoogleCredentials source = GoogleCredentials.create(new AccessToken("test-source-token", null));
    GoogleCredentials result =
        new GoogleCredentialsFactory(() -> source, path -> null).create(auth, SCOPES);
    assertThat(((ImpersonatedCredentials) result).getAccount())
        .isEqualTo("123456789-compute@developer.gserviceaccount.com");
  }

  @Test
  void bindsTheTwoStandardFields() {
    GoogleAuthProperties auth =
        new Binder(
                new MapConfigurationPropertySource(
                    Map.of(
                        "auth.credentials-file",
                        "test-credentials.json",
                        "auth.impersonate-service-account",
                        TARGET)))
            .bind("auth", GoogleAuthProperties.class)
            .get();
    assertThat(auth.getCredentialsFile()).isEqualTo("test-credentials.json");
    assertThat(auth.getImpersonateServiceAccount()).isEqualTo(TARGET);
  }

  private static GoogleCredentialsFactory fileFactory(TrackingStream stream) {
    return new GoogleCredentialsFactory(
        () -> {
          throw new AssertionError("must not discover ADC when a file is selected");
        },
        path -> stream);
  }

  private static ImpersonatedCredentials impersonatedCredentials() throws IOException {
    GoogleAuthProperties auth = new GoogleAuthProperties();
    auth.setImpersonateServiceAccount(TARGET);
    GoogleCredentials source = GoogleCredentials.create(new AccessToken("test-source-token", null));
    return (ImpersonatedCredentials)
        new GoogleCredentialsFactory(() -> source, path -> null).create(auth, SCOPES);
  }

  private static class TrackingStream extends ByteArrayInputStream {
    private boolean closed;

    TrackingStream(String contents) {
      super(contents.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }
  }
}
