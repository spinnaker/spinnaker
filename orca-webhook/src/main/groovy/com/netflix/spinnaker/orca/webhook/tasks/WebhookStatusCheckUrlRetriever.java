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

package com.netflix.spinnaker.orca.webhook.tasks;

import com.jayway.jsonpath.JsonPath;
import com.netflix.spinnaker.orca.webhook.pipeline.WebhookStage;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

/** Retrieves status check url from the response provided. */
class WebhookStatusCheckUrlRetriever {

  private final Logger log = LoggerFactory.getLogger(getClass());
  private static final Pattern URL_SCHEME = Pattern.compile("(.*)://(.*)");

  public String getStatusCheckUrl(ResponseEntity response, WebhookStage.StageData stageData) {

    String statusCheckUrl = null;

    switch (stageData.statusUrlResolution) {
      case getMethod:
        statusCheckUrl = stageData.url;
        break;
      case locationHeader:
        statusCheckUrl = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        break;
      case webhookResponse:
        statusCheckUrl = JsonPath.compile(stageData.statusUrlJsonPath).read(response.getBody());
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + stageData.statusUrlResolution);
    }
    log.info("Web hook status check url as resolved: {}", statusCheckUrl);

    // Preserve the protocol scheme of original webhook that was called, when calling for status
    // check of a webhook.
    if (statusCheckUrl != null && !statusCheckUrl.equals(stageData.url)) {
      Matcher statusUrlMatcher = URL_SCHEME.matcher(statusCheckUrl);
      URI statusCheckUri = URI.create(statusCheckUrl).normalize();
      String statusCheckHost = statusCheckUri.getHost();

      URI webHookUri = URI.create(stageData.url).normalize();
      String webHookHost = webHookUri.getHost();
      if (webHookHost.equals(statusCheckHost)
          && !webHookUri.getScheme().equals(statusCheckUri.getScheme())
          && statusUrlMatcher.find()) {
        // Same hosts keep the original protocol scheme of the webhook that was originally set.
        statusCheckUrl = webHookUri.getScheme() + "://" + statusUrlMatcher.group(2);
        log.info("Adjusted Web hook status check url: {}", statusCheckUrl);
      }
    }

    return statusCheckUrl;
  }
}
