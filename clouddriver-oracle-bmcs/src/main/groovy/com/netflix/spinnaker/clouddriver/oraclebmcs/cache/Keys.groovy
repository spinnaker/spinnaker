/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.cache

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.oraclebmcs.OracleBMCSCloudProvider
import groovy.util.logging.Slf4j

@Slf4j
class Keys {

  static enum Namespace {

    NETWORKS,
    SUBNETS,
    IMAGES,
    SECURITY_GROUPS

    static String provider = OracleBMCSCloudProvider.ID

    final String ns

    private Namespace() {

      def parts = name().split('_')

      ns = parts.tail().inject(new StringBuilder(parts.head().toLowerCase())) { val, next -> val.append(next.charAt(0)).append(next.substring(1).toLowerCase()) }
    }

    String toString() {
      ns
    }
  }

  static Map<String, String> parse(String key) {
    def parts = key.split(':')

    if (parts.length < 2 || parts[0] != OracleBMCSCloudProvider.ID) {
      return null
    }

    def result = [provider: parts[0], type: parts[1]]

    if (result.provider != Namespace.provider) {
      return null
    }

    switch (result.type) {
      case Namespace.NETWORKS.ns:
        result << [
          name   : parts[2],
          id     : parts[3],
          region : parts[4],
          account: parts[5]
        ]
        break
      case Namespace.SUBNETS.ns:
        result << [
          id     : parts[2],
          region : parts[3],
          account: parts[4]
        ]
        break
      case Namespace.IMAGES.ns:
        result << [
          account: parts[2],
          region : parts[3],
          imageId: parts[4]
        ]
        break
      case Namespace.SECURITY_GROUPS.ns:
        def names = Names.parseName(parts[2])
        result << [
          application: names.app,
          name       : parts[2],
          id         : parts[3],
          region     : parts[4],
          account    : parts[5]
        ]
        break
      default:
        return null
        break
    }

    result
  }

  static String getSecurityGroupKey(String securityGroupName,
                                    String securityGroupId,
                                    String region,
                                    String account) {
    "$OracleBMCSCloudProvider.ID:${Namespace.SECURITY_GROUPS}:${securityGroupName}:${securityGroupId}:${region}:${account}"
  }

  static String getImageKey(String account, String region, String imageId) {
    "$OracleBMCSCloudProvider.ID:${Namespace.IMAGES}:${account}:${region}:${imageId}"
  }

  static String getNetworkKey(String networkName,
                              String networkId,
                              String region,
                              String account) {
    "$OracleBMCSCloudProvider.ID:${Namespace.NETWORKS}:${networkName}:${networkId}:${region}:${account}"
  }

  static String getSubnetKey(String subnetId,
                             String region,
                             String account) {
    "$OracleBMCSCloudProvider.ID:${Namespace.SUBNETS}:${subnetId}:${region}:${account}"
  }

}
