/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client

import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.TimeUnit

/*
 * These tests all communicate with dockerhub (index.docker.io), and will either fail
 * with an exception indicating a network or HTTP error, or will fail to load data
 * from dockerhub.
 */
@Ignore
class DockerRegistryClientSpec extends Specification {
  private static final REPOSITORY1 = "library/ubuntu"

  @Shared
  DockerRegistryClient client

  def setupSpec() {
    client = new DockerRegistryClient("https://index.docker.io", "", "", "", TimeUnit.MINUTES.toMillis(1), 100)
  }

  void "DockerRegistryClient should request a real set of tags."() {
    when:
      DockerRegistryTags result = client.getTags(REPOSITORY1)

    then:
      result.name == REPOSITORY1
      result.tags.size() > 0
  }

  void "DockerRegistryClient should validate that it is pointing at a v2 endpoint."() {
    when:
      // Can only fail due to an exception thrown here.
      client.checkV2Availability()

    then:
      true
  }
}
