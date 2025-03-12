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

package com.netflix.spinnaker.echo.slack

import com.netflix.spinnaker.echo.config.SlackAppProperties
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import groovy.transform.Canonical
import groovy.util.logging.Slf4j
import org.springframework.http.HttpHeaders
import org.springframework.http.RequestEntity
import org.apache.commons.codec.binary.Hex

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.InvalidKeyException
import java.time.Duration
import java.time.Instant

@Slf4j
@Canonical
class SlackAppService extends SlackService {

  SlackAppService(SlackClient client, SlackAppProperties config) {
    super(client, config)
  }

  // Reference: https://api.slack.com/docs/verifying-requests-from-slack
  // FIXME (lfp): this algorithm works as I've validated it against the sample data provided in the Slack documentation,
  //  but it doesn't work with our requests and signing secret for some reason. I've reached out to Slack support but
  //  have not received any definitive answers yet.
  void verifySignature(RequestEntity<String> request, boolean preventReplays = true) {
    HttpHeaders headers = request.getHeaders()
    String body = request.getBody()
    String timestamp = headers['X-Slack-Request-Timestamp'].first()
    String signature = headers['X-Slack-Signature'].first()

    if (preventReplays &&
      (Instant.ofEpochSecond(Long.valueOf(timestamp)) + Duration.ofMinutes(5)).isBefore(Instant.now())) {
      // The request timestamp is more than five minutes from local time. It could be a replay attack.
      throw new InvalidRequestException("Slack request timestamp is older than 5 minutes. Replay attack?")
    }

    String calculatedSignature = calculateSignature(timestamp, body)

    if (calculatedSignature != signature) {
      throw new InvalidRequestException("Invalid Slack signature header.")
    }
  }

  String calculateSignature(String timestamp, String body, String version = "v0") {
    try {
      // For some reason, Spring URL-decodes asterisks in the body (but not other URL-encoded characters :-P)
      body = body.replaceAll(/\*/, "%2A")
      String signatureBaseString = "$version:$timestamp:$body"
      Mac mac = Mac.getInstance("HmacSHA256")
      SecretKeySpec secretKeySpec = new SecretKeySpec(config.signingSecret.getBytes(), "HmacSHA256")
      mac.init(secretKeySpec)
      byte[] digest = mac.doFinal(signatureBaseString.getBytes())
      return "$version=${Hex.encodeHex(digest).toString()}"
    } catch (InvalidKeyException e) {
      throw new InvalidRequestException("Invalid key exception verifying Slack request signature.")
    }
  }

  void verifyToken(String receivedToken) {
    if (receivedToken != config.verificationToken) {
      throw new InvalidRequestException("Token received from Slack does not match verification token.")
    }
  }
}
