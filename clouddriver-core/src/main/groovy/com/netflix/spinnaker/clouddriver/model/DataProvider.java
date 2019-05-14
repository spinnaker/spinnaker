/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.model;

import java.io.OutputStream;
import java.util.Map;

public interface DataProvider {
  enum IdentifierType {
    Static,
    Adhoc
  }

  /**
   * Fetch a specific object from a bucket that has been explicitly configured.
   *
   * <p>Filters are supported if the configured object is of type `list`.
   *
   * @return string/list/map depending on type of configured object
   */
  Object getStaticData(String id, Map<String, Object> filters);

  /**
   * Stream a specified object from a bucket.
   *
   * <p>Both the object key and bucket name must be whitelisted.
   */
  void getAdhocData(String groupId, String bucketId, String objectId, OutputStream outputStream);

  /** @return true if this identifier is supported by the data provider */
  boolean supportsIdentifier(IdentifierType identifierType, String id);

  /** @return the account name corresponding to the provided identifier */
  String getAccountForIdentifier(IdentifierType identifierType, String id);
}
