/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.echo.pubsub.utils

import spock.lang.Specification
import spock.lang.Subject

class MessageArtifactTranslatorSpec extends Specification {

  @Subject
  MessageArtifactTranslator messageArtifactTranslator

  def setup() {
    messageArtifactTranslator = new MessageArtifactTranslator()
  }

  def "defaults to empty list with null or empty message payload"() {
    given:
    String payload = ""

    when:
    def artifacts = messageArtifactTranslator.parseArtifacts(null)

    then:
    artifacts instanceof List
    artifacts.size() == 0

    when:
    artifacts = messageArtifactTranslator.parseArtifacts(payload)

    then:
    artifacts instanceof List
    artifacts.size() == 0
  }

  def "can translate a singleton artifact list"() {
    given:
    String template = '''
    [
        {
            "reference": "{{ id }}",
            "type": "gcs/object"
        }
    ]
    '''
    String payload = '''
    {
        "id": "gs://this/is/my/id"
    }
    '''
    messageArtifactTranslator.jinjaTemplate = template

    when:
    def artifacts = messageArtifactTranslator.parseArtifacts(payload)

    then:
    artifacts.size() == 1
    artifacts.any { it.getReference() == 'gs://this/is/my/id' && it.getType() == 'gcs/object' }
  }

  def "can deserialize several artifacts from a list with jinja magic"() {
    String template = '''
    [
        {% for artifact in artifacts %}
        {
            "reference": "{{ artifact['id'] }}",
            "type": "{{ artifact['type'] }}"
        }{% if !loop.last %},{% endif %}
        {% endfor %}
    ]
    '''
    String payload = '''
    {
        "artifacts": [
            {"id": "gs://this/is/my/id1", "type": "gcs/object"},
            {"id": "gs://this/is/my/id2", "type": "tarball"},
            {"id": "gs://this/is/my/id3", "type": "binary"}
        ]
    }
    '''
    messageArtifactTranslator.jinjaTemplate = template

    when:
    def artifacts = messageArtifactTranslator.parseArtifacts(payload)

    then:
    artifacts.size() == 3
    artifacts.any { it.getReference() == 'gs://this/is/my/id1' && it.getType() == 'gcs/object' }
    artifacts.any { it.getReference() == 'gs://this/is/my/id2' && it.getType() == 'tarball' }
    artifacts.any { it.getReference() == 'gs://this/is/my/id3' && it.getType() == 'binary' }
  }
}
