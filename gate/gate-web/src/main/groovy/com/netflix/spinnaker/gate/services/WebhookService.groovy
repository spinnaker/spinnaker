/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.internal.EchoService
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.springframework.beans.factory.annotation.Autowired
import okhttp3.MediaType
import okhttp3.RequestBody
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import io.cloudevents.CloudEvent

import java.nio.charset.StandardCharsets

@Component
class WebhookService {

  @Autowired(required = false)
  EchoService echoService

  @Autowired
  OrcaServiceSelector orcaServiceSelector

  /**
   * The headers a sender attaches to a webhook that echo reads, matched case-insensitively. This is
   * an allowlist on purpose: it keeps the caller's credentials (Authorization, Cookie) and
   * Spinnaker's own identity headers from being copied from an anonymous request.
   *
   * <ul>
   *   <li>X-Hub-Signature, X-Hub-Signature-256: HMAC of the body (GitHub, Bitbucket, Gitea)
   *   <li>X-Event-Key: event type (Bitbucket)
   *   <li>X-GitHub-Event: event type (GitHub)
   *   <li>X-Gitea-Signature, X-Gitea-Event: HMAC of the body and event type (Gitea)
   * </ul>
   */
  static final List<String> FORWARDED_WEBHOOK_HEADERS = [
    'X-Hub-Signature',
    'X-Hub-Signature-256',
    'X-Event-Key',
    'X-GitHub-Event',
    'X-Gitea-Signature',
    'X-Gitea-Event'
  ].asImmutable()

  private static final MediaType JSON = MediaType.get('application/json; charset=utf-8')

  /**
   * @param body the request body exactly as the sender sent it; may be null or empty
   * @param senderHeaders the sender's headers; only {@link #FORWARDED_WEBHOOK_HEADERS} are passed on
   */
  Map webhooks(String type, String source, byte[] body, HttpHeaders senderHeaders) {
    // An empty body used to arrive as an empty Map, which was sent to echo as "{}".
    RequestBody requestBody = RequestBody.create(body ? body : '{}'.getBytes(StandardCharsets.UTF_8), JSON)

    Map<String, String> headers = [:]
    FORWARDED_WEBHOOK_HEADERS.each { String name ->
      String value = senderHeaders.getFirst(name)
      if (value != null) {
        headers[name] = value
      }
    }

    return AuthenticatedRequest.allowAnonymous({
      Retrofit2SyncCall.execute(echoService.webhooks(type, source, requestBody, headers))
    })
  }

  Map webhooks(String source, CloudEvent cdEvent, String ceDataJsonString) {
    return AuthenticatedRequest.allowAnonymous( {
      Retrofit2SyncCall.execute(echoService.webhooks(source, cdEvent, ceDataJsonString, cdEvent.getId(), cdEvent.getSpecVersion().V1.toString(), cdEvent.getType(), cdEvent.getSource().toString()))
    })
  }

  /** Retrieve preconfigured webhook definitions from orca. */
  List<Map<String, Object>> preconfiguredWebhooks() {
    return AuthenticatedRequest.allowAnonymous({
      Retrofit2SyncCall.execute(orcaServiceSelector.select().preconfiguredWebhooks())
    })
  }
}
