/*
 * Copyright 2020 Netflix, Inc.
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
 */

package com.netflix.spinnaker.front50.plugins

import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.nio.file.Files

class CachingPluginBinaryStorageServiceSpec extends Specification {

  PluginBinaryStorageService delegate = Mock()
  @Subject PluginBinaryStorageService subject = new CachingPluginBinaryStorageService(delegate)

  def "cache on store"() {
    when:
    subject.store("hello.zip", "world".bytes)

    then:
    1 * delegate.store("hello.zip", "world".bytes)
    getCacheFile("hello.zip") == "world"
  }

  def "clear cache on delete"() {
    when:
    subject.delete("hello.zip")

    then:
    1 * delegate.delete("hello.zip")
    !getCachePath("hello.zip").toFile().exists()
  }

  def "store on load"() {
    when:
    subject.load("hello.zip")

    then:
    1 * delegate.load("hello.zip") >> "mom"
    getCacheFile("hello.zip") == "mom"
  }

  private Path getCachePath(String key) {
    return subject.CACHE_PATH.resolve(key)
  }

  private String getCacheFile(String key) {
    return new String(Files.readAllBytes(getCachePath(key)))
  }
}
