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
package com.netflix.spinnaker.kato.model.aws


/**
 * Different ways to enable payment for EC2 instance usage, with different pricing models. Each type of pricing has a
 * different source of data for collecting the latest prices.
 */
enum InstancePriceType {

  /**
   * Pricing for instances that have not been reserved and are needed right away.
   */
  ON_DEMAND('http://aws.amazon.com/ec2/pricing/json/', 'linux-od.json'),

  /**
   * Instances priced based on the spot market for instances that are not needed right away.
   */
  SPOT('https://spot-price.s3.amazonaws.com/', 'spot.js')

  /**
   * Creates enum from a String. Ignores case and defaults to ON_DEMAND if there is not a match.
   *
   * @param value String representation
   * @return new enum based on value
   */
  static InstancePriceType parse(String value) {
    values().find { it.name().equalsIgnoreCase(value) } ?: ON_DEMAND
  }

  /**
   * The beginning of the URL for getting the data file for pricing, when fetching live data remotely.
   */
  String dataSourceUrlBase

  /**
   * The name of the file to parse for pricing data, either on the live remote server or in the local file system
   * when developing offline or running unit tests.
   */
  String dataSourceFileName

  /**
   * Constructor for each price type.
   *
   * @param dataSourceUrlBase first part of remote URL for pricing data file when working online
   * @param dataSourceFileName name of pricing data file both online via URL and offline from file system
   */
  InstancePriceType(String dataSourceUrlBase, String dataSourceFileName) {
    this.dataSourceUrlBase = dataSourceUrlBase
    this.dataSourceFileName = dataSourceFileName
  }

  /**
   * Creates the full URL of the remote data file for the data for a type of pricing.
   *
   * @return String the full remote URL of the data file
   */
  String getUrl() {
    "${dataSourceUrlBase}${dataSourceFileName}"
  }
}
