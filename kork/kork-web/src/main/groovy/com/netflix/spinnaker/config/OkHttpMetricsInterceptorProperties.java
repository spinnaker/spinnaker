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
package com.netflix.spinnaker.config;

import jakarta.annotation.PostConstruct;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties("ok-http-client.interceptor")
public class OkHttpMetricsInterceptorProperties {

  /** If set to true, will skip header check completely. */
  private boolean skipHeaderCheck = false;

  /**
   * If Skip header check is set to false and regex is set, header check will be skipped for all
   * endpoints except for the ones that match the provided regex.
   */
  private String headerCheckPattern = null;

  /** This is derived from the headerCheckPattern specified */
  private Pattern endPointPatternForHeaderCheck;

  public boolean isSkipHeaderCheck() {
    return skipHeaderCheck;
  }

  public void setSkipHeaderCheck(boolean skipHeaderCheck) {
    this.skipHeaderCheck = skipHeaderCheck;
  }

  public String getHeaderCheckPattern() {
    return headerCheckPattern;
  }

  public void setHeaderCheckPattern(String headerCheckPattern) {
    this.headerCheckPattern = headerCheckPattern;
  }

  public Pattern getEndPointPatternForHeaderCheck() {
    return endPointPatternForHeaderCheck;
  }

  @PostConstruct
  private void initEndPointPatternForHeaderCheck() {
    if (!StringUtils.isEmpty(this.headerCheckPattern)) {
      endPointPatternForHeaderCheck = Pattern.compile(this.headerCheckPattern);
    }
  }
}
