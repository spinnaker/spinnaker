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

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Loads one credential source, then optionally impersonates a service account. Credential files
 * must be trusted operator configuration, never arbitrary end-user input. No failure falls back to
 * another identity. Clients retain the returned credentials so the Google library manages refresh.
 */
public class GoogleCredentialsFactory {
  private static final String CLOUD_PLATFORM_SCOPE =
      "https://www.googleapis.com/auth/cloud-platform";
  private static final int IMPERSONATION_LIFETIME_SECONDS = 3600;

  @FunctionalInterface
  public interface ApplicationDefaultCredentials {
    GoogleCredentials load() throws IOException;
  }

  @FunctionalInterface
  public interface CredentialFileOpener {
    InputStream open(String path) throws IOException;
  }

  private final ApplicationDefaultCredentials applicationDefaultCredentials;
  private final CredentialFileOpener credentialFileOpener;

  public GoogleCredentialsFactory() {
    this(GoogleCredentials::getApplicationDefault, path -> Files.newInputStream(Path.of(path)));
  }

  /** Allows integrations to preserve their existing secret-aware file resolution. */
  public GoogleCredentialsFactory(CredentialFileOpener credentialFileOpener) {
    this(GoogleCredentials::getApplicationDefault, credentialFileOpener);
  }

  public GoogleCredentialsFactory(
      ApplicationDefaultCredentials applicationDefaultCredentials,
      CredentialFileOpener credentialFileOpener) {
    this.applicationDefaultCredentials = Objects.requireNonNull(applicationDefaultCredentials);
    this.credentialFileOpener = Objects.requireNonNull(credentialFileOpener);
  }

  public GoogleCredentials create(GoogleAuthProperties auth, Collection<String> scopes)
      throws IOException {
    Objects.requireNonNull(
        auth, "auth must not be null; omit it or use an empty auth object for ADC");
    auth.validate();
    List<String> requestedScopes = List.copyOf(scopes);
    if (requestedScopes.isEmpty() || requestedScopes.stream().anyMatch(String::isBlank)) {
      throw new IllegalArgumentException("Google API scopes must not be empty or blank");
    }

    GoogleCredentials source;
    if (auth.getCredentialsFile() == null) {
      source = applicationDefaultCredentials.load();
    } else {
      try (InputStream stream = credentialFileOpener.open(auth.getCredentialsFile())) {
        source = GoogleCredentials.fromStream(Objects.requireNonNull(stream, "credential stream"));
      }
    }

    if (auth.getImpersonateServiceAccount() == null) {
      return source.createScoped(requestedScopes);
    }
    return ImpersonatedCredentials.create(
        source.createScoped(List.of(CLOUD_PLATFORM_SCOPE)),
        auth.getImpersonateServiceAccount(),
        null /* delegates */,
        requestedScopes,
        IMPERSONATION_LIFETIME_SECONDS);
  }
}
