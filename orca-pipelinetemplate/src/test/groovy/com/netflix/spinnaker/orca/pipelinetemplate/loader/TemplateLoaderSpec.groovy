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

import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateLoaderException
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration
import spock.lang.Specification
import spock.lang.Subject;

class TemplateLoaderSpec extends Specification {
  def schemeLoader = Mock(TemplateSchemeLoader)

  @Subject
  def templateLoader = new TemplateLoader([schemeLoader])

  void "should return a LIFO list of pipeline templates"() {
    when:
    def pipelineTemplates = templateLoader.load(new TemplateConfiguration.TemplateSource(source: "template1.json"))

    then:
    pipelineTemplates*.id == ["3", "2", "1"]

    1 * schemeLoader.load(new URI("template1.json")) >> { new PipelineTemplate(id: "1", source: "template2.json") }
    1 * schemeLoader.load(new URI("template2.json")) >> { new PipelineTemplate(id: "2", source: "template3.json") }
    1 * schemeLoader.load(new URI("template3.json")) >> { new PipelineTemplate(id: "3", source: null) }
    _ * schemeLoader.supports(_) >> { return true }
    0 * _
  }

  void "should raise exception if cycle detected"() {
    when:
    templateLoader.load(new TemplateConfiguration.TemplateSource(source: "template1.json"))

    then:
    def e = thrown(TemplateLoaderException)
    e.message == "Illegal cycle detected loading pipeline template 'template2.json'"

    2 * schemeLoader.load(new URI("template1.json")) >> { new PipelineTemplate(id: "1", source: "template2.json") }
    1 * schemeLoader.load(new URI("template2.json")) >> { new PipelineTemplate(id: "2", source: "template1.json") }
    _ * schemeLoader.supports(_) >> { return true }
    0 * _
  }

  void "should raise exception if no loader found for scheme"() {
    when:
    templateLoader.load(new TemplateConfiguration.TemplateSource(source: "file://template1.json"))

    then:
    def e = thrown(TemplateLoaderException)
    e.message == "No TemplateSchemeLoader found for 'file'"

    _ * schemeLoader.supports(_) >> { return false }
    0 * _
  }

  void "should raise exception if `source` is not a valid URI"() {
    when:
    templateLoader.load(new TemplateConfiguration.TemplateSource(source: "::"))

    then:
    def e = thrown(TemplateLoaderException)
    e.message == "Invalid URI '::'"
  }
}
