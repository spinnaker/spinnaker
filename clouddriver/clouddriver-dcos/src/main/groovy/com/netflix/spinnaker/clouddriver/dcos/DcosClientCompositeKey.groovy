/*
 * Copyright 2017 Cerner Corporation
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

package com.netflix.spinnaker.clouddriver.dcos

import com.google.common.base.Strings
import org.slf4j.LoggerFactory;

public class DcosClientCompositeKey {
  private final static def LOGGER = LoggerFactory.getLogger(DcosClientCompositeKey)
  private final static def KEY_SEPARATOR = '|'

  private String accountName
  private String clusterName

  private DcosClientCompositeKey(String accountName, String clusterName) {
    this.accountName = accountName
    this.clusterName = clusterName
  }

  public String getAccount() {
    accountName
  }

  public String getCluster() {
    clusterName
  }

  @Override
  public String toString() {
    return "${accountName}${KEY_SEPARATOR}${clusterName}"
  }

  public static Optional<DcosClientCompositeKey> parseFrom(String key) {
    if (isBlank(key)) {
      return Optional.empty()
    }

    def parts = key.split(KEY_SEPARATOR)

    if (parts.size() != 2) {
      return Optional.empty()
    }

    return Optional.of(new DcosClientCompositeKey(parts[0], parts[1]))
  }

  public static Optional<DcosClientCompositeKey> buildFrom(String accountName, String clusterName) {
    if (isBlank(accountName) ||
      accountName.contains(KEY_SEPARATOR) ||
      isBlank(clusterName) ||
      clusterName.contains(KEY_SEPARATOR)) {
      return Optional.empty()
    }

    return Optional.of(new DcosClientCompositeKey(accountName, clusterName))
  }

  public static Optional<DcosClientCompositeKey> parseFromVerbose(String key) {
    if (isBlank(key)) {
      LOGGER.error("DC/OS composite key was empty, null, or blank.")
      return Optional.empty()
    }

    def parts = key.split(KEY_SEPARATOR)

    if (parts.size() != 2) {
      LOGGER.error("A DC/OS client composite key [${key}] should only contain 2 parts, but contained [${parts.size()}")
      return Optional.empty()
    }

    return Optional.of(new DcosClientCompositeKey(parts[0], parts[1]))
  }

  public static Optional<DcosClientCompositeKey> buildFromVerbose(String accountName, String clusterName) {
    if (isBlank(accountName)) {
      LOGGER.error("Account name was empty, null, or blank.")
      return Optional.empty()
    }
    if (isBlank(clusterName)) {
      LOGGER.error("Cluster name was empty, null, or blank.")
      return Optional.empty()
    }
    if (accountName.contains(KEY_SEPARATOR)) {
      LOGGER.error("Account name [${accountName}] contained the key separator [${KEY_SEPARATOR}] used by composite key making it invalid.")
      return Optional.empty()
    }
    if (clusterName.contains(KEY_SEPARATOR)) {
      LOGGER.error("Cluster name [${clusterName}] contained the key separator [${KEY_SEPARATOR}] used by composite key making it invalid.")
      return Optional.empty()
    }

    return Optional.of(new DcosClientCompositeKey(accountName, clusterName))
  }

  private static boolean isBlank(String str) {
    Strings.nullToEmpty(str).trim().isEmpty()
  }
}
