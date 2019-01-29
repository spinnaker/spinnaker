/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.cache

import com.google.common.base.CaseFormat
import com.google.common.collect.ImmutableSet
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.cache.KeyParser
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider.ID

@Component("AmazonInfraKeys")
class Keys implements KeyParser {

  static enum Namespace {
    CERTIFICATES,
    SECURITY_GROUPS(["application", "name", "id", "region", "account", "vpcId"]),
    SUBNETS,
    VPCS,
    KEY_PAIRS,
    INSTANCE_TYPES,
    ELASTIC_IPS,
    STACKS,
    ON_DEMAND

    final String ns
    final Set<String> fields

    private Namespace(List<String> keyFields = []) {
      ns = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name())
      fields = ImmutableSet.copyOf(["provider", "type"] + keyFields);
    }

    String toString() {
      ns
    }
  }
  private static final Set<String> PARSEABLE_FIELDS =
    ImmutableSet.builder().addAll(Namespace.SECURITY_GROUPS.fields).build()

  @Override
  String getCloudProvider() {
    return ID
  }

  @Override
  Map<String, String> parseKey(String key) {
    return parse(key)
  }

  @Override
  Boolean canParseType(String type) {
    return Namespace.values().any { it.ns == type }
  }

  @Override
  Boolean canParseField(String field) {
    return PARSEABLE_FIELDS.contains(field)
  }

  static Map<String, String> parse(String key) {
    def parts = key.split(':')

    if (parts.length < 2) {
      return null
    }

    def result = [provider: parts[0], type: parts[1]]

    if (result.provider != ID) {
      return null
    }

    switch (result.type) {
      case Namespace.SECURITY_GROUPS.ns:
        def names = Names.parseName(parts[2])
        result << [application: names.app, name: parts[2], id: parts[3], region: parts[4], account: parts[5], vpcId: parts[6] == "null" ? null : parts[6]]
        break
      case Namespace.VPCS.ns:
        result << [id: parts[2], account: parts[3], region: parts[4]]
        break
      case Namespace.SUBNETS.ns:
        result << [id: parts[2], account: parts[3], region: parts[4]]
        break
      case Namespace.CERTIFICATES.ns:
        result << [id: parts[2], account: parts[3], region: parts[4], type: parts[5]]
        break
      case Namespace.KEY_PAIRS.ns:
        result << [id: parts[2], account: parts[3], region: parts[4]]
        break
      case Namespace.INSTANCE_TYPES.ns:
        result << [name: parts[2], account: parts[3], region: parts[4]]
        break
      case Namespace.ELASTIC_IPS.ns:
        result << [address: parts[2], account: parts[3], region: parts[4]]
        break
      case Namespace.STACKS.ns:
        result << [id: parts[2], account: parts[3], region: parts[4]]
        break;
      default:
        return null
        break
    }

    result
  }

  static String getSecurityGroupKey(String securityGroupName,
                                    String securityGroupId,
                                    String region,
                                    String account,
                                    String vpcId) {
    "$ID:${Namespace.SECURITY_GROUPS}:${securityGroupName}:${securityGroupId}:${region}:${account}:${vpcId}"
  }

  static String getSubnetKey(String subnetId,
                             String region,
                             String account) {
    "$ID:${Namespace.SUBNETS}:${subnetId}:${account}:${region}"
  }

  static String getCertificateKey(String certificateId,
                             String region,
                             String account,
                             String certificateType) {
    "$ID:${Namespace.SUBNETS}:${certificateId}:${account}:${region}:${certificateType}"
  }

  static String getVpcKey(String vpcId,
                          String region,
                          String account) {
    "$ID:${Namespace.VPCS}:${vpcId}:${account}:${region}"
  }

  static String getKeyPairKey(String keyName,
                              String region,
                              String account) {
    "$ID:${Namespace.KEY_PAIRS}:${keyName}:${account}:${region}"
  }

  static String getInstanceTypeKey(String instanceType,
                                   String region,
                                   String account) {
    "$ID:${Namespace.INSTANCE_TYPES}:${instanceType}:${account}:${region}"
  }

  static String getElasticIpKey(String ipAddress,
                                String region,
                                String account) {
    "$ID:${Namespace.ELASTIC_IPS}:${ipAddress}:${account}:${region}"
  }

  static String getCloudFormationKey(String stackId,
                                     String region,
                                     String accountName) {
    "$ID:${Namespace.STACKS}:${accountName}:${region}:${stackId}"
  }
}
