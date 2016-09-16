/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.client

/**
 * Methods for interacting with the Openstack Swift API.
 */
interface OpenstackSwiftProvider {

  /**
   * Returns the content of a Swift object.
   * @param container the container that holds the object
   * @param name the name the object within the container
   * @return contents of the object
   */
  String readSwiftObject(final String region, final String container, final String name)
}
