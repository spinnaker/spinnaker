/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.kork.secrets.engines;

import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import com.netflix.spinnaker.kork.secrets.NoopUserSecretData;
import com.netflix.spinnaker.kork.secrets.SecretEngine;
import com.netflix.spinnaker.kork.secrets.SecretReference;
import com.netflix.spinnaker.kork.secrets.user.UserSecret;
import com.netflix.spinnaker.kork.secrets.user.UserSecretMetadata;
import com.netflix.spinnaker.kork.secrets.user.UserSecretReference;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

/**
 * Secret engine that returns its value ("v") parameter which can be null if not provided. Used for
 * testing.
 */
@Component
public class NoopSecretEngine implements SecretEngine {
  private static final String IDENTIFIER = "noop";

  @Override
  public String identifier() {
    return IDENTIFIER;
  }

  @Override
  public byte[] decrypt(EncryptedSecret encryptedSecret) {
    String value = encryptedSecret.getRequiredParameter(NoopSecretParameter.VALUE);
    return value.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  @Nonnull
  public UserSecret decrypt(@Nonnull UserSecretReference reference) {
    String value = reference.getRequiredParameter(NoopSecretParameter.VALUE);
    return UserSecret.builder()
        .data(new NoopUserSecretData(value))
        .metadata(
            UserSecretMetadata.builder().type("opaque").encoding("json").roles(List.of()).build())
        .build();
  }

  @Override
  public void validate(EncryptedSecret encryptedSecret) {
    encryptedSecret.getRequiredParameter(NoopSecretParameter.VALUE);
  }

  @Override
  public void validate(@Nonnull UserSecretReference reference) {
    reference.getRequiredParameter(NoopSecretParameter.VALUE);
  }

  @Override
  public void clearCache() {}

  @Override
  public boolean supports(@Nonnull SecretReference reference) {
    return reference.getEngineIdentifier().equals(IDENTIFIER);
  }

  @Override
  @Nonnull
  public String resolve(@Nonnull SecretReference reference) {
    return reference.getRequiredParameter(NoopSecretParameter.VALUE);
  }
}
