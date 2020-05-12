/*
 * Copyright 2019 Google, Inc.
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

import com.netflix.spinnaker.clouddriver.artifacts.CredentialReader;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Optional;
import org.apache.commons.codec.binary.Base64;

@NonnullByDefault
public interface BasicAuth {
  Optional<String> getUsername();

  Optional<String> getPassword();

  Optional<String> getUsernamePasswordFile();

  default Optional<String> getBasicAuthHeader() {
    String usernamePassword = null;
    if (getUsernamePasswordFile().isPresent()) {
      usernamePassword = CredentialReader.credentialsFromFile(getUsernamePasswordFile().get());
    } else if (getUsername().isPresent() && getPassword().isPresent()) {
      usernamePassword = getUsername().get() + ":" + getPassword().get();
    }

    return Optional.ofNullable(usernamePassword)
        .map(s -> "Basic " + Base64.encodeBase64String(s.getBytes()));
  }
}
