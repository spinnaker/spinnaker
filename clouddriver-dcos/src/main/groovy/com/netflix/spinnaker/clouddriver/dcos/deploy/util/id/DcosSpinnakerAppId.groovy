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

package com.netflix.spinnaker.clouddriver.dcos.deploy.util.id

import com.netflix.frigga.Names
import org.slf4j.LoggerFactory

import static com.google.common.base.Strings.nullToEmpty

/**
 * Represents a hierarchical Spinnaker specific application identifier for DCOS.
 * Structure - /account/region/app-stack-detail-sequence
 */
class DcosSpinnakerAppId {
  private final static def LOGGER = LoggerFactory.getLogger(DcosSpinnakerAppId)
  public final static def SAFE_REGION_SEPARATOR = "_"

  private final def marathonPath

  private DcosSpinnakerAppId(final MarathonPathId marathonPath) {
    this.marathonPath = marathonPath
  }

  public String getAccount() {
    marathonPath.first().get()
  }

  /**
   * @return The canonical DC/OS "region" (a.k.a the full group path in which the marathon application lives)
   *         including backslashes. This is returned as a relative path, meaning no preceeding backslash. Will never
   *         be null.
   *         <p/>
   *         Deemed unsafe because various Spinnaker components have trouble with a region with backslashes.
   *         <p/>
   *         Ex: {@code foo/bar}
   * @see #getSafeGroup()
   */
  public String getUnsafeGroup() {
    marathonPath.tail().parent().relative().toString()
  }

  /**
   * @return The "safe" DC/OS region (a.k.a the group in which the marathon application lives). This is returned as a
   *         relative path, meaning no preceeding underscore. Will never be null.
   *         <p/>
   *         Deemed safe because backslashes are replaced with underscores.
   *         <p/>
   *         Ex: {@code acct_foo_bar}
   * @see #getUnsafeGroup()
   */
  public String getSafeGroup() {
    unsafeGroup.replaceAll(MarathonPathId.PART_SEPARATOR, SAFE_REGION_SEPARATOR)
  }

  public String getNamespace() {
    marathonPath.parent().toString()
  }

  public Names getServerGroupName() {
    Names.parseName(marathonPath.last().get())
  }

  @Override
  public String toString() {
    marathonPath.toString()
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true
    }
    if (!(o instanceof DcosSpinnakerAppId)) {
      return false
    }
    def dcosPathId = (DcosSpinnakerAppId) o
    return dcosPathId.toString() == toString()
  }

  @Override
  public int hashCode() {
    return toString().hashCode()
  }

  public static Optional<DcosSpinnakerAppId> parse(String marathonAppId) {
    parseFrom(marathonAppId, false)
  }

  public static Optional<DcosSpinnakerAppId> parse(String marathonAppId, String account) {
    parseFrom(marathonAppId, account, false)
  }

  public static Optional<DcosSpinnakerAppId> from(String account, String group, String serverGroupName) {
    buildFrom(account, group, serverGroupName, false)
  }

  public static Optional<DcosSpinnakerAppId> parseVerbose(String marathonAppId) {
    parseFrom(marathonAppId, true)
  }

  public static Optional<DcosSpinnakerAppId> parseVerbose(String marathonAppId, String account) {
    parseFrom(marathonAppId, account, true)
  }

  public static Optional<DcosSpinnakerAppId> fromVerbose(String account, String group, String serverGroupName) {
    buildFrom(account, group, serverGroupName, true)
  }

  private static Optional<DcosSpinnakerAppId> parseFrom(String marathonAppId, boolean log) {
    def marathonPath

    try {
      marathonPath = MarathonPathId.parse(nullToEmpty(marathonAppId)).absolute()
    } catch (IllegalArgumentException e) {
      logError(log, e.message)
      return Optional.empty()
    }

    if (marathonPath.size() < 2) {
      logError(log, "A part of the DCOS Spinnaker App ID was missing [${marathonPath.toString()}].")
      return Optional.empty()
    }

    def service = Names.parseName(marathonPath.last().get())

    if (nullToEmpty(service.app).trim().empty) {
      logError(log, "The server group app should not be null, empty, or blank.")
      return Optional.empty()
    }
    if (service.sequence < 0) {
      logError(log, "The server group sequence should not be negative or null.")
      return Optional.empty()
    }

    return Optional.of(new DcosSpinnakerAppId(marathonPath))
  }

  private static Optional<DcosSpinnakerAppId> parseFrom(String marathonAppId, String account, boolean log) {
    def dcosSpinnakerAppId = parseFrom(marathonAppId, log)

    if (!dcosSpinnakerAppId.isPresent()) {
      return Optional.empty()
    }

    if (dcosSpinnakerAppId.get().account != account) {
      logError(log, "The account [${account}] given does not match the account within the app id [${dcosSpinnakerAppId.get().account}].")
      return Optional.empty()
    }

    dcosSpinnakerAppId
  }

  private static Optional<DcosSpinnakerAppId> buildFrom(
    final String account, final String group, final String serverGroupName, boolean log) {
    if (nullToEmpty(account).trim().empty) {
      logError(log, "The account should not be null, empty, or blank.")
      return Optional.empty()
    }
    if (nullToEmpty(serverGroupName).trim().empty) {
      logError(log, "The serverGroupName should not be null, empty, or blank.")
      return Optional.empty()
    }
    if (account.contains(MarathonPathId.PART_SEPARATOR)) {
      logError(log, "The account [${account}] should not contain any '/' characters.")
      return Optional.empty()
    }
    if (serverGroupName.contains(MarathonPathId.PART_SEPARATOR)) {
      logError(log, "The serverGroupName [${serverGroupName}] should not contain any '/' characters.")
      return Optional.empty()
    }

    def marathonPath

    try {
      def marathonPathString = group ? "/${account}/${group.replaceAll(SAFE_REGION_SEPARATOR, MarathonPathId.PART_SEPARATOR)}/${serverGroupName}" : "/${account}/${serverGroupName}"
      marathonPath = MarathonPathId.parse(marathonPathString)
    } catch (IllegalArgumentException e) {
      logError(log, e.message)
      return Optional.empty()
    }

    def service = Names.parseName(serverGroupName)

    if (nullToEmpty(service.app).trim().empty) {
      logError(log, "The server group app should not be null, empty, or blank.")
      return Optional.empty()
    }
    if (service.sequence < 0) {
      logError(log, "The server group sequence should not be negative or null.")
      return Optional.empty()
    }

    return Optional.of(new DcosSpinnakerAppId(marathonPath))
  }

  static void logError(boolean log, String message) {
    if (log) {
      LOGGER.error(message)
    }
  }
}
