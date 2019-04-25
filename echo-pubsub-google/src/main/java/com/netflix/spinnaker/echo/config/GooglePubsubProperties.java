/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

package com.netflix.spinnaker.echo.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@ConfigurationProperties(prefix = "pubsub.google")
@Validated
public class GooglePubsubProperties {

  @Valid
  private List<GooglePubsubSubscription> subscriptions = new ArrayList<>();

  @Valid
  private List<GooglePubsubPublisherConfig> publishers = new ArrayList<>();

  @Builder
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class GooglePubsubSubscription {

    @NotEmpty
    private String name;

    @NotEmpty
    private String project;

    @NotEmpty
    private String subscriptionName;

    @NotNull
    @Builder.Default
    private Integer ackDeadlineSeconds = 10;

    // Not required since subscriptions can be public.
    private String jsonPath;

    private String templatePath;

    @Builder.Default
    private MessageFormat messageFormat = MessageFormat.CUSTOM;

    public InputStream readTemplatePath() {
      try {
        if (messageFormat == null || messageFormat == MessageFormat.CUSTOM) {
          if (StringUtils.isEmpty(templatePath)) {
            return null;
          } else {
            return new FileInputStream(new File(templatePath));
          }
        } else {
          return getClass().getResourceAsStream(messageFormat.jarPath);
        }
      } catch (IOException e) {
        throw new RuntimeException(
          "Failed to read template in subscription " + name + ": " + e.getMessage(), e);
      }
    }
  }

  @Data
  @NoArgsConstructor
  public static class GooglePubsubPublisherConfig {

    @NotEmpty
    private String name;

    @NotEmpty
    private String topicName;

    @NotEmpty
    private String project;

    // Optional. Uses Application Default Credentials if not set.
    private String jsonPath;

    private Content content = Content.ALL;

    @Min(value = 1L, message = "Batch count threshold must be a positive integer. Defaults to 10.")
    private Long batchCountThreshold = 10L;

    @Min(value = 1L, message = "Delay milliseconds threshold must be a positive integer. Defaults to 1000.")
    private Long delayMillisecondsThreshold = 1000L;
  }

  public static enum Content {
    ALL,
    NOTIFICATIONS
  }

  public static enum MessageFormat {
    GCS("/gcs.jinja"),
    GCR("/gcr.jinja"),
    GCB("/gcb.jinja"),
    CUSTOM();

    private String jarPath = "";

    MessageFormat(String jarPath) {
      this.jarPath = jarPath;
    }

    MessageFormat() {
    }
  }
}
