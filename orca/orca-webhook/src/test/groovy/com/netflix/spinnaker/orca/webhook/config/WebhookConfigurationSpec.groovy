/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.webhook.config

import org.springframework.http.MediaType
import org.springframework.mock.http.MockHttpOutputMessage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll;

class WebhookConfigurationSpec extends Specification {
  @Subject
  def messageConverter = new WebhookConfiguration.MapToStringHttpMessageConverter();

  @Unroll
  def "should convert map to string"() {
    given:
    def outputMessage = new MockHttpOutputMessage()

    when:
    messageConverter.write(source, MediaType.APPLICATION_FORM_URLENCODED, outputMessage)

    then:
    outputMessage.getBodyAsString() == expectedString

    where:
    source                                       || expectedString
    ["foo": "bar"]                               || "foo=bar"
    ["foo": "bar", "this is a sentence": "true"] || "foo=bar&this+is+a+sentence=true"
    ["foo": "bar", "this is a sentence": true]   || "foo=bar&this+is+a+sentence=true"
    ["foo&bar": "bar&baz"]                       || "foo%26bar=bar%26baz"
    ["foo=bar": "bar=baz"]                       || "foo%3Dbar=bar%3Dbaz"
  }

  def "should support 'application/x-www-form-urlencoded'"() {
    expect:
    !messageConverter.canWrite(Map.class, MediaType.APPLICATION_JSON)
    !messageConverter.canWrite(Collection.class, MediaType.APPLICATION_FORM_URLENCODED)
    messageConverter.canWrite(Map.class, MediaType.APPLICATION_FORM_URLENCODED)
  }
}
