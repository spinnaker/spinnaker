/*
 * Copyright 2015 Netflix, Inc.
 * Copyright 2023 Apple, Inc.
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

package com.netflix.spinnaker.gate.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Getter
@Setter
public class Service {
  private boolean enabled = true;
  private String baseUrl;
  private MultiBaseUrl shards;
  private Map<String, Object> config = new LinkedHashMap<>();

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  public List<BaseUrl> getBaseUrls() {
    if (shards != null) {
      String baseUrl = shards.getBaseUrl();
      if (StringUtils.hasLength(baseUrl)) {
        return List.of(new BaseUrl(baseUrl));
      }
      List<BaseUrl> baseUrls = shards.getBaseUrls();
      if (!CollectionUtils.isEmpty(baseUrls)) {
        return baseUrls;
      }
    }
    return List.of(new BaseUrl(baseUrl));
  }

  @Getter
  @Setter
  public static class MultiBaseUrl {
    private String baseUrl;
    private List<BaseUrl> baseUrls;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @RequiredArgsConstructor
  public static class BaseUrl {
    @Nonnull private String baseUrl;
    private int priority = 1;
    private Map<String, Object> config = new LinkedHashMap<>();
  }
}
