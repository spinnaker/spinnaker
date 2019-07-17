/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.webhook.service;

import org.springframework.web.client.RestTemplate;

public interface RestTemplateProvider {
  /** @return true if this {@code RestTemplateProvider} supports the given url */
  boolean supports(String targetUrl);

  /**
   * Provides an opportunity for a {@code RestTemplateProvider} to modify any aspect of the target
   * url, including: scheme, host, path
   *
   * <p>This exists to support use cases wherein a webhook request may need to be proxied.
   *
   * @return a potentially modified target url
   */
  default String getTargetUrl(String targetUrl) {
    return targetUrl;
  }

  /** @return a configured {@code RestTemplate} */
  RestTemplate getRestTemplate(String targetUrl);
}
