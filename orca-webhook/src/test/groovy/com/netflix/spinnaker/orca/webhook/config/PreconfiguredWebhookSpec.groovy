/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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

package com.netflix.spinnaker.orca.webhook.config

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.webhook.config.WebhookProperties.StatusUrlResolution.*

class PreconfiguredWebhookSpec extends Specification {

  def "getPreconfiguredFields should return all fields if all stage configuration fields are populated"() {
    setup:
    def preconfiguredWebhook = createPreconfiguredWebhook()

    when:
    def fields = preconfiguredWebhook.preconfiguredProperties

    then:
    fields == ["url", "customHeaders", "method", "payload", "waitForCompletion", "statusUrlResolution", "statusUrlJsonPath",
      "statusJsonPath", "progressJsonPath", "successStatuses", "canceledStatuses", "terminalStatuses"]
  }

  def "getPreconfiguredFields should return empty list if no stage configuration fields are populated"() {
    setup:
    def preconfiguredWebhook = new WebhookProperties.PreconfiguredWebhook()

    when:
    def fields = preconfiguredWebhook.preconfiguredProperties

    then:
    fields == []
  }

  @Unroll
  def "noUserConfigurableFields should be correct based on the configuration values"() {
    when:
    def preconfiguredWebhook = createPreconfiguredWebhook()
    preconfiguredWebhook.with {
      url = _url
      waitForCompletion = _waitForCompletion
      statusUrlResolution = _statusUrlResolution
      statusJsonPath = _statusJsonPath
    }

    then:
    preconfiguredWebhook.noUserConfigurableFields() == expected

    where:
    _url   | _waitForCompletion  | _statusUrlResolution | _statusJsonPath || expected
    null   | null                | null                 | null            || false
    null   | false               | null                 | null            || false
    "foo"  | null                | null                 | null            || false
    "foo"  | false               | null                 | null            || true
    null   | true                | null                 | null            || false
    "foo"  | true                | locationHeader       | null            || true
    "foo"  | true                | getMethod            | null            || true
    "foo"  | true                | getMethod            | "bar"           || true
    "foo"  | true                | webhookResponse      | null            || false
    "foo"  | true                | webhookResponse      | "bar"           || true
  }

  static WebhookProperties.PreconfiguredWebhook createPreconfiguredWebhook() {
    def customHeaders = new HttpHeaders()
    customHeaders.put("header", ["value1", "value2"])
    return new WebhookProperties.PreconfiguredWebhook(
      url: "url", customHeaders: customHeaders, method: HttpMethod.POST, payload: "payload",
      waitForCompletion: true, statusUrlResolution: webhookResponse,
      statusUrlJsonPath: "statusUrlJsonPath", statusJsonPath: "statusJsonPath", progressJsonPath: "progressJsonPath",
      successStatuses: "successStatuses", canceledStatuses: "canceledStatuses", terminalStatuses: "terminalStatuses"
    )
  }
}
