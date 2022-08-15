/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.clouddriver.config;

import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NB: The explicit @Getter, @Setter and @NoArgsConstructor on some of these inner classes is
 * required. Some weird behaviour going on when using @Data prevented fields from being populated.
 * See spinnaker/#6704.
 */
@Data
@ConfigurationProperties
public class CloudDriverConfigurationProperties {

  @Getter
  @Setter
  @NoArgsConstructor
  public static class BaseUrl {
    private String baseUrl;
    private int priority = 1;
    private Map<String, Object> config;

    BaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }
  }

  @Data
  public static class MultiBaseUrl {
    private String baseUrl;
    private List<BaseUrl> baseUrls;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  public static class CloudDriver {
    private String baseUrl;
    private MultiBaseUrl readonly;
  }

  private BaseUrl mort;
  private BaseUrl oort;
  private BaseUrl kato;
  private CloudDriver clouddriver;

  String getCloudDriverBaseUrl() {
    if (clouddriver != null && clouddriver.baseUrl != null) {
      return clouddriver.baseUrl;
    } else if (kato != null && kato.baseUrl != null) {
      return kato.baseUrl;
    } else if (oort != null && oort.baseUrl != null) {
      return oort.baseUrl;
    } else if (mort != null && mort.baseUrl != null) {
      return mort.baseUrl;
    }

    return null;
  }

  List<BaseUrl> getCloudDriverReadOnlyBaseUrls() {
    if (clouddriver != null
        && clouddriver.readonly != null
        && clouddriver.readonly.baseUrl != null) {
      BaseUrl url = new BaseUrl(clouddriver.readonly.baseUrl);
      return List.of(url);
    } else if (clouddriver != null
        && clouddriver.readonly != null
        && clouddriver.readonly.baseUrls != null) {
      return clouddriver.readonly.baseUrls;
    }

    BaseUrl url = new BaseUrl(getCloudDriverBaseUrl());
    return List.of(url);
  }
}
