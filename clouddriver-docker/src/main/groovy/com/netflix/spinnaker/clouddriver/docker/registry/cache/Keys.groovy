/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.docker.registry.cache

class Keys {
  static enum Namespace {
    TAGGED_IMAGE,
    IMAGE_ID,

    static String provider = "dockerRegistry"

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

    if (parts.length < 2) {
      return null
    }

    def result = [provider: parts[0], type: parts[1]]

    if (result.provider != Namespace.provider) {
      return null
    }

    switch (result.type) {
      case Namespace.TAGGED_IMAGE.ns:
        result << [account: parts[2], repository: parts[3], tag: parts[4]]
        break
      case Namespace.IMAGE_ID.ns:
        result << [imageId: parts[2]]
        break
      default:
        return null
        break
    }

    result
  }

  static String getTaggedImageKey(String account, String repository, String tag) {
    "${Namespace.provider}:${Namespace.TAGGED_IMAGE}:${account}:${repository}:${tag}"
  }

  static String getImageIdKey(String imageId) {
    "${Namespace.provider}:${Namespace.IMAGE_ID}:${imageId}"
  }
}
