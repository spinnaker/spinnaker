/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.model

/**
 * A representation of a network
 */
public interface Network {
  /**
   * The cloud provider associated with this network
   *
   * @return
   */
  String getCloudProvider()

  /**
   * The ID associated with this network
   *
   * @return
   */
  String getId()

  /**
   * The name for this network
   *
   * @return
   */
  String getName()

  /**
   * The account associated with this network
   *
   * @return
   */
  String getAccount()

  /**
   * The region associated with this network
   *
   * @return
   */
  String getRegion()
}
