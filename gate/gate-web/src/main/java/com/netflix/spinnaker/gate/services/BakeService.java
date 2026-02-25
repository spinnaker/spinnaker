/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.gate.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.netflix.spinnaker.gate.services.internal.RoscoServiceSelector;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("services.rosco.defaults")
public class BakeService {

  private RoscoServiceSelector roscoServiceSelector;

  // Default bake options from configuration.
  private List<BakeOptions> bakeOptions;

  // If set, use bake options defined in gate.yml instead of calling rosco
  private boolean useDefaultBakeOptions;

  @Autowired
  public BakeService(Optional<RoscoServiceSelector> roscoServiceSelector) {
    this.roscoServiceSelector = roscoServiceSelector.orElse(null);
  }

  public Object bakeOptions() {
    return (roscoServiceSelector != null && !useDefaultBakeOptions)
        ? Retrofit2SyncCall.execute(roscoServiceSelector.withLocation(null).bakeOptions())
        : this.bakeOptions;
  }

  public Object bakeOptions(String cloudProvider) {
    if (roscoServiceSelector != null) {
      return Retrofit2SyncCall.execute(
          roscoServiceSelector.withLocation(null).bakeOptions(cloudProvider));
    }
    BakeOptions bakeOpts =
        this.bakeOptions.stream()
            .filter(opts -> cloudProvider.equals(opts.getCloudProvider()))
            .findFirst()
            .orElse(null);
    if (bakeOpts != null) {
      return bakeOpts;
    }
    throw new IllegalArgumentException(
        "Bake options for cloud provider " + cloudProvider + " not found");
  }

  public String lookupLogs(String region, String statusId) {
    if (roscoServiceSelector != null) {
      Map logsMap =
          Retrofit2SyncCall.execute(
              roscoServiceSelector.withLocation(region).lookupLogs(region, statusId));

      if (logsMap != null && logsMap.get("logsContent") != null) {
        return "<pre>" + logsMap.get("logsContent") + "</pre>";
      } else {
        throw new NotFoundException("Bake logs not found.");
      }
    } else {
      throw new IllegalArgumentException("Bake logs retrieval not supported.");
    }
  }

  @Data
  public static class BakeOptions {
    private String cloudProvider;
    private List<BaseImage> baseImages;
  }

  @Data
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class BaseImage {
    private String id;
    private String shortDescription;
    private String detailedDescription;
    private String displayName;
    private String packageType;
    private List<String> vmTypes;
  }
}
