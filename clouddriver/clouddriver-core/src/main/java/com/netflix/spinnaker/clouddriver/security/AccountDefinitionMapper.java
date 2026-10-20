/*
 * Copyright 2021 Apple Inc.
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

import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import com.netflix.spinnaker.kork.secrets.SecretSession;
import com.netflix.spinnaker.kork.secrets.user.UserSecretReference;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Maps account definitions to and from strings. Only {@link CredentialsDefinition} classes
 * annotated with a {@link com.fasterxml.jackson.annotation.JsonTypeName} will be considered. {@link
 * UserSecretReference} URIs may be used for credentials values which will be replaced with an
 * appropriate string for the secret along with recording an associated account name for time of use
 * permission checks on the user secret. Traditional {@link EncryptedSecret} URIs are also
 * supported.
 */
@NonnullByDefault
@RequiredArgsConstructor
public class AccountDefinitionMapper {

  private final ObjectMapper objectMapper;
  private final AccountDefinitionSecretManager secretManager;
  private final SecretSession secretSession;

  public String serialize(CredentialsDefinition definition) throws JacksonException {
    return objectMapper.writeValueAsString(definition);
  }

  public CredentialsDefinition deserialize(String string) throws JacksonException {
    ObjectNode account = (ObjectNode) objectMapper.readTree(string);
    String accountName = account.required("name").asText();
    Iterator<Map.Entry<String, JsonNode>> it = account.properties().iterator();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> field = it.next();
      JsonNode node = field.getValue();
      if (node.isTextual()) {
        String text = node.asText();
        Optional<String> plaintext;
        if (UserSecretReference.isUserSecret(text)) {
          UserSecretReference ref = UserSecretReference.parse(text);
          plaintext = Optional.of(secretManager.getUserSecretString(ref, accountName));
        } else if (EncryptedSecret.isEncryptedSecret(text)) {
          plaintext = Optional.ofNullable(secretSession.decrypt(text));
        } else {
          plaintext = Optional.empty();
        }
        plaintext.ifPresent(value -> field.setValue(objectMapper.stringNode(value)));
      }
    }
    return objectMapper.convertValue(account, CredentialsDefinition.class);
  }
}
