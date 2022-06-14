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
import com.netflix.spinnaker.kork.annotations.Beta;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.List;
import javax.annotation.Nullable;

/** Provides CRUD persistence operations for account {@link CredentialsDefinition} instances. */
@Beta
@NonnullByDefault
public interface AccountDefinitionRepository {
  /**
   * Looks up an account definition using the account name.
   *
   * @param name account name to look up
   * @return the configured account definition or null if none is found
   */
  @Nullable
  CredentialsDefinition getByName(String name);

  /**
   * Lists account definitions for a given account type. This API allows for infinite scrolling
   * style pagination using the {@code limit} and {@code startingAccountName} parameters.
   *
   * @param typeName account type to search for (the value of the @JsonTypeName annotation on the
   *     corresponding CredentialsDefinition class
   * @param limit max number of entries to return
   * @param startingAccountName where to begin results list if specified or start at the beginning
   *     if null
   * @return list of stored account definitions matching the given account type name (sorted
   *     alphabetically)
   */
  List<? extends CredentialsDefinition> listByType(
      String typeName, int limit, @Nullable String startingAccountName);

  /**
   * Lists account definitions for a given account type. Account types correspond to the value in
   * the {@code @JsonTypeName} annotation present on the corresponding {@code CredentialsDefinition}
   * class.
   *
   * @param typeName account type to search for
   * @return list of all stored account definitions matching the given account type name
   */
  List<? extends CredentialsDefinition> listByType(String typeName);

  /**
   * Creates a new account definition using the provided data. Secrets should use {@code
   * UserSecretReference} encrypted URIs (e.g., {@code
   * secret://secrets-manager?r=us-west-2&s=my-account-credentials}) when the underlying storage
   * provider does not support row-level encryption or equivalent security features. Encrypted URIs
   * will only be decrypted when loading account definitions, not when storing them. Note that
   * account definitions correspond to the JSON representation of the underlying {@link
   * CredentialsDefinition} object along with a JSON type discriminator field with the key {@code
   * type} and value of the corresponding {@code @JsonTypeName} annotation. Note that in addition to
   * user secret URIs, traditional {@code EncryptedSecret} URIs (like {@code
   * encrypted:secrets-manager!r:us-west-2!s:my-account-credentials}) are also supported when
   * Clouddriver is configured with global secret engines.
   *
   * @param definition account definition to store as a new account
   */
  void create(CredentialsDefinition definition);

  /**
   * Creates or updates an account definition using the provided data. This is also known as an
   * upsert operation. See {@link #create(CredentialsDefinition)} for more details.
   *
   * @param definition account definition to save
   */
  void save(CredentialsDefinition definition);

  /**
   * Updates an existing account definition using the provided data. See details in {@link
   * #create(CredentialsDefinition)} for details on the format.
   *
   * @param definition updated account definition to replace an existing account
   * @see #create(CredentialsDefinition)
   */
  void update(CredentialsDefinition definition);

  /**
   * Deletes an account by name.
   *
   * @param name name of account to delete
   */
  void delete(String name);

  /**
   * Looks up the revision history of an account given its name. Revisions are sorted by latest
   * version first.
   *
   * @param name account name to look up history for
   * @return history of account updates for the given account name
   */
  List<Revision> revisionHistory(String name);

  /**
   * Provides metadata for an account definition revision when making updates to an account via
   * {@link AccountDefinitionRepository} APIs.
   */
  class Revision {
    private final int version;
    private final long timestamp;
    private final @Nullable CredentialsDefinition account;

    /** Constructs a revision entry with a version and account definition. */
    public Revision(int version, long timestamp, @Nullable CredentialsDefinition account) {
      this.version = version;
      this.timestamp = timestamp;
      this.account = account;
    }

    /** Returns the version number of this revision. Versions start at 1 and increase from there. */
    public int getVersion() {
      return version;
    }

    /**
     * Returns the timestamp (in millis since the epoch) corresponding to when this revision was
     * made.
     */
    public long getTimestamp() {
      return timestamp;
    }

    /**
     * Returns the account definition used in this revision. Returns {@code null} when this revision
     * corresponds to a deletion.
     */
    @Nullable
    public CredentialsDefinition getAccount() {
      return account;
    }
  }
}
