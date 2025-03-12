/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate.loader

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateLoaderException
import spock.lang.Specification
import spock.lang.Unroll;

class FileTemplateSchemeLoaderSpec extends Specification {
  def schemeLoader = new FileTemplateSchemeLoader(new ObjectMapper())

  @Unroll
  void "should support json/yaml/yml extensions"() {
    expect:
    schemeLoader.supports(new URI(uri)) == shouldSupport

    where:
    uri                                || shouldSupport
    "file:///tmp/foobar.json"          || true
    "file:///tmp/foobar.yaml"          || true
    "file:///tmp/foobar.yml"           || true
    "file:///tmp/foobar.txt"           || false
    "http://www.google.com/foobar.yml" || false
  }

  void "should raise exception when uri does not exist"() {
    when:
    schemeLoader.load(new URI("file:///tmp/does-not-exist.yml"))

    then:
    def e = thrown(TemplateLoaderException)
    e.cause instanceof FileNotFoundException
  }

  void "should load simple pipeline template"() {
    given:
    def uri = getClass().getResource("/templates/simple-001.yml").toURI()

    when:
    def pipelineTemplate = schemeLoader.load(uri)

    then:
    pipelineTemplate.id == "simpleTemplate"
  }
}
