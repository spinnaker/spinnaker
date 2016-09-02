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
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup

import com.fasterxml.jackson.annotation.JsonCreator

enum UserDataType {
  URL('url'), TEXT('text')

  String type

  UserDataType(String type) {
    this.type = type
  }

  @Override
  String toString() {
    type
  }

  @JsonCreator
  static String fromType(String type) {
    switch (type) {
      case URL.type:
        URL.name().toLowerCase()
        break
      case TEXT.type:
        TEXT.name().toLowerCase()
        break
      default:
        throw new IllegalArgumentException("Invalid enum type: $type")
    }
  }

  static UserDataType fromString(String value) {
    switch (value) {
      case URL.toString():
        URL
        break
      case TEXT.toString():
        TEXT
        break
      default:
        throw new IllegalArgumentException("Invalid enum type: $value")
    }
  }

}
