/*
 * Copyright 2022 Apple, Inc.
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
 *
 */

package com.netflix.spinnaker.kork.secrets.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import com.netflix.spinnaker.kork.secrets.InvalidSecretFormatException;
import com.netflix.spinnaker.kork.secrets.SecretDecryptionException;
import com.netflix.spinnaker.kork.secrets.SecretReference;
import com.netflix.spinnaker.kork.secrets.UnsupportedSecretEngineException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserSecretService {
  private final UserSecretManager secretManager;
  private final UserSecretReferenceCache cache = new UserSecretReferenceCache();

  @PostAuthorize("hasPermission(returnObject, 'READ')")
  public UserSecret getUserSecret(UserSecretReference ref) {
    return secretManager.getUserSecret(ref);
  }

  /**
   * Replaces top level fields containing user secret URIs and external secret URIs with their
   * secret values for the given resource id. User secret references used by the resource are cached
   * until the next time a resource with the same id is processed.
   *
   * @param resourceId id of resource being processed for secrets
   * @param object resource to replace secret references
   * @param fieldNamesToSkip field names for which we will not replace secret references
   * @return the updated resource
   * @throws UnsupportedSecretEngineException if any secret reference does not have a corresponding
   *     secret engine
   * @throws UnsupportedUserSecretEngineException if any secret engine does not support user secrets
   * @throws MissingUserSecretMetadataException if any secret is missing its {@link
   *     UserSecretMetadata}
   * @throws InvalidUserSecretMetadataException if any secret has metadata that cannot be parsed
   * @throws InvalidSecretFormatException if any secret reference has validation errors
   * @throws SecretDecryptionException if any secret reference cannot be fetched
   * @throws NoSuchElementException if no secret data exists for any referenced keys
   * @throws UnsupportedOperationException if no key is specified and the corresponding secret does
   *     not support scalar resolution
   * @see #isTrackingUserSecretsForResource(String)
   * @see #findUnauthorizedUserSecretUris(String, Set)
   */
  public ObjectNode replaceSecretReferences(
      String resourceId, ObjectNode object, Set<String> fieldNamesToSkip) {
    List<SecretField<UserSecretReference>> userSecretFields = new ArrayList<>();
    List<SecretField<EncryptedSecret>> externalSecretFields = new ArrayList<>();

    // first, scan for secret references in top level string fields
    var iterator = object.fields();
    while (iterator.hasNext()) {
      var entry = iterator.next();
      String fieldName = entry.getKey();
      JsonNode field = entry.getValue();
      String value = field.textValue();
      if (value != null && !fieldNamesToSkip.contains(fieldName)) {
        if (UserSecretReference.isUserSecret(value)) {
          UserSecretReference ref = UserSecretReference.parse(value);
          userSecretFields.add(new SecretField<>(fieldName, ref));
        } else if (EncryptedSecret.isEncryptedSecret(value)) {
          EncryptedSecret ref = EncryptedSecret.parse(value);
          externalSecretFields.add(new SecretField<>(fieldName, ref));
        }
      }
    }
    if (userSecretFields.isEmpty() && externalSecretFields.isEmpty()) {
      // clear out stale references
      cache.clearReferences(resourceId);
      return object;
    }

    // next, fetch and replace secret references
    ObjectNode updates = object.objectNode();
    for (SecretField<EncryptedSecret> field : externalSecretFields) {
      String secret = secretManager.getExternalSecretString(field.secretReference);
      updates.set(field.fieldName, object.textNode(secret));
    }

    List<UserSecretReference> references = new ArrayList<>(userSecretFields.size());
    for (SecretField<UserSecretReference> field : userSecretFields) {
      references.add(field.secretReference);
      UserSecret userSecret = secretManager.getUserSecret(field.secretReference);
      String secret = userSecret.getSecretString(field.secretReference);
      updates.set(field.fieldName, object.textNode(secret));
    }

    // finally, update tracking info and return
    cacheReferences(resourceId, references);
    return object.setAll(updates);
  }

  /**
   * Mark the provided references to user secrets as being linked to a resource ID (e.g. an account
   * name). Accessing this resource will require having access to the associated user secrets.
   *
   * @param resourceId the ID of a resource using these secrets
   * @param references the references to user secrets to cache
   * @see #isTrackingUserSecretsForResource(String)
   */
  public void cacheReferences(String resourceId, Collection<UserSecretReference> references) {
    cache.cacheReferences(resourceId, references);
  }

  /**
   * Checks whether the provided resource id is being tracked for user secrets. Resources with no
   * user secrets or that do not exist should return {@code false}.
   */
  public boolean isTrackingUserSecretsForResource(String resourceId) {
    return cache.hasAnyReferences(resourceId);
  }

  public Set<String> findUnauthorizedUserSecretUris(String resourceId, Set<String> userRoles) {
    Set<String> unauthorized = new HashSet<>();
    for (var reference : cache.getReferences(resourceId)) {
      var secret = secretManager.getUserSecret(reference);
      var secretRoles = secret.getRoles();
      if (!secretRoles.isEmpty() && Collections.disjoint(userRoles, secretRoles)) {
        unauthorized.add(reference.getUri());
      }
    }
    return unauthorized;
  }

  /**
   * Attempts to fetch and decrypt the given parsed external secret reference. If this is unable to
   * fetch the secret, then this will throw an exception.
   *
   * @param reference parsed external secret reference to fetch
   * @throws SecretDecryptionException if the external secret does not have a corresponding secret
   *     engine or cannot be fetched
   * @throws InvalidSecretFormatException if the external secret reference is invalid
   */
  public void checkExternalSecret(EncryptedSecret reference) {
    secretManager.getExternalSecret(reference);
  }

  /**
   * Fetches and decrypts the given parsed external secret reference encoded as a string. External
   * secrets are secrets available through {@link EncryptedSecret} URIs.
   *
   * @param reference parsed external secret reference to fetch
   * @return the decrypted external secret string
   * @throws SecretDecryptionException if the external secret does not have a corresponding secret
   *     engine or cannot be fetched
   * @throws InvalidSecretFormatException if the external secret reference is invalid
   */
  public String getExternalSecretString(EncryptedSecret reference) {
    return secretManager.getExternalSecretString(reference);
  }

  private record SecretField<T extends SecretReference>(String fieldName, T secretReference) {}
}
