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

import org.slf4j.LoggerFactory

import static com.google.common.base.Strings.nullToEmpty

/**
 * Represents a hierarchical Spinnaker specific load balancer identifier for DCOS.
 * Structure - /account/loadBalancerName
 */
class DcosSpinnakerLbId {
  private final static def LOGGER = LoggerFactory.getLogger(DcosSpinnakerLbId)
  public final static def SAFE_NAME_SEPARATOR = "_"

  private final def marathonPath

  public DcosSpinnakerLbId(final MarathonPathId marathonPath) {
    this.marathonPath = marathonPath
  }

  public String getAccount() {
    marathonPath.first().get()
  }

  public String getLoadBalancerName() {
    marathonPath.last().get()
  }

  public String getLoadBalancerHaproxyGroup() {
    marathonPath.relative().toString().replaceAll(MarathonPathId.PART_SEPARATOR, SAFE_NAME_SEPARATOR)
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
    if (o == null || getClass() != o.getClass()) {
      return false
    }
    def dcosPathId = (DcosSpinnakerLbId) o
    return dcosPathId.toString() == toString()
  }

  @Override
  public int hashCode() {
    return toString().hashCode()
  }

  public static Optional<DcosSpinnakerLbId> parse(String id) {
    parseFrom(id, false)
  }

  public static Optional<DcosSpinnakerLbId> parse(String id, String account) {
    parseFrom(id, account, false)
  }

  public static Optional<DcosSpinnakerLbId> from(String account, String loadBalancerName) {
    buildFrom(account, loadBalancerName, false)
  }

  public static Optional<DcosSpinnakerLbId> parseVerbose(String id) {
    parseFrom(id, true)
  }

  public static Optional<DcosSpinnakerLbId> parseVerbose(String id, String account) {
    parseFrom(id, account, true)
  }

  public static Optional<DcosSpinnakerLbId> fromVerbose(String account, String loadBalancerName) {
    buildFrom(account, loadBalancerName, true)
  }

  private static Optional<DcosSpinnakerLbId> parseFrom(final String id, final boolean log) {
    def marathonPath

    try {
      marathonPath = MarathonPathId.parse(id)
    } catch (IllegalArgumentException e) {
      logError(log, e.message)
      return Optional.empty()
    }

    if (marathonPath.size() != 2) {
      logError(log, "A DCOS Spinnaker LB ID should only contain 3 parts [${marathonPath.toString()}].")
      return Optional.empty()
    }

    def dcosSpinnakerLbId = new DcosSpinnakerLbId(marathonPath)

    Optional.of(dcosSpinnakerLbId)
  }

  private static Optional<DcosSpinnakerLbId> parseFrom(final String id, final String account, final boolean log) {
    def dcosSpinnakerLbId = parseFrom(id, log)

    if (!dcosSpinnakerLbId.isPresent()) {
      return Optional.empty()
    }

    if (dcosSpinnakerLbId.get().account != account) {
      logError(log, "The account [${account}] given does not match the account within the load balancer id [${dcosSpinnakerLbId.get().account}].")
      return Optional.empty()
    }

    dcosSpinnakerLbId
  }

  private static Optional<DcosSpinnakerLbId> buildFrom(
    final String account, final String loadBalancerName, final boolean log) {
    if (nullToEmpty(account).trim().empty) {
      logError(log, "The account should not be null, empty, or blank.")
      return Optional.empty()
    }
    if (nullToEmpty(loadBalancerName).trim().empty) {
      logError(log, "The loadBalancerName should not be null, empty, or blank.")
      return Optional.empty()
    }

    if (account.contains(MarathonPathId.PART_SEPARATOR)) {
      logError(log, "The account [${account}] should not contain any '/' characters.")
      return Optional.empty()
    }
    if (loadBalancerName.contains(MarathonPathId.PART_SEPARATOR)) {
      logError(log, "The loadBalancerName [${loadBalancerName}] should not contain any '/' characters.")
      return Optional.empty()
    }

    def marathonPath

    try {
      marathonPath = MarathonPathId.parse("/${account}/${loadBalancerName}")
    } catch (IllegalArgumentException e) {
      logError(log, e.message)
      return Optional.empty()
    }

    Optional.of(new DcosSpinnakerLbId(marathonPath))
  }

  static void logError(boolean log, String message) {
    if (log) {
      LOGGER.error(message)
    }
  }
}
