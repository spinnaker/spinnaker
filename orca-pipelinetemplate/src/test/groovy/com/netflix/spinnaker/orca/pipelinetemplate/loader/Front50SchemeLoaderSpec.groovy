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
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateLoaderException
import retrofit.RetrofitError
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class Front50SchemeLoaderSpec extends Specification {

  Front50Service front50Service = Mock()

  @Subject
  def schemeLoader = new Front50SchemeLoader(Optional.of(front50Service), new ObjectMapper())

  @Unroll
  void 'should support spinnaker scheme'() {
    expect:
    schemeLoader.supports(new URI(uri)) == shouldSupport

    where:
    uri || shouldSupport
    "spinnaker://myTemplateId" || true
    "http://myTemplateId"      || false
  }

  void 'should raise exception when uri does not exist'() {
    when:
    schemeLoader.load(new URI('spinnaker://myTemplateId'))

    then:
    front50Service.getPipelineTemplate("myTemplateId") >> {
      throw RetrofitError.networkError("http://front50/no-exist", new IOException("resource not found"))
    }
    def e = thrown(TemplateLoaderException)
    e.cause instanceof RetrofitError
  }

  void 'should load simple pipeline template'() {
    given:
    Map<String, Object> template = [
      schema: '1',
      id: 'myTemplateId',
      metadata: [
        name: 'My Template'
      ]
    ]

    when:
    def result = schemeLoader.load(new URI('spinnaker://myTemplateId'))

    then:
    front50Service.getPipelineTemplate('myTemplateId') >> { return template }
    result.schema == template.schema
    result.id == template.id
    result.metadata.name == template.metadata.name
  }
}
