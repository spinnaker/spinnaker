/*
 * Copyright 2025 Salesforce, Inc.
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

package com.netflix.spinnaker.orca.webhook.service;

import java.util.Map;
import org.springframework.http.HttpHeaders;

/**
 * If a bean that implements this interface is present in the Spring context, the return value of
 * the getHeaders method determines the http request headers that webhook stages send.
 */
public interface WebhookAccountProcessor {
  /**
   * Determine the http request headers that webhook stages send. Throw an exception to fail the
   * webook stage before sending an http request.
   *
   * @param account the name of the Spinnaker account associated with the webhook stage (possibly
   *     null or empty)
   * @param accountDetails details about the Spinnaker account (possibly null)
   * @param customHeaders the custom headers specified in the webhook stage (possibly null)
   * @return the http request headers to send
   */
  HttpHeaders getHeaders(
      String account, Map<String, Object> accountDetails, Map<String, Object> customHeaders);
}
