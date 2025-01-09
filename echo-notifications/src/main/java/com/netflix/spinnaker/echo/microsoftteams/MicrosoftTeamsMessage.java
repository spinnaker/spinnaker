/*
 * Copyright 2020 Cerner Corporation
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

package com.netflix.spinnaker.echo.microsoftteams;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.echo.microsoftteams.api.MicrosoftTeamsSection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MicrosoftTeamsMessage {
  // API Reference:
  // https://docs.microsoft.com/en-us/outlook/actionable-messages/message-card-reference
  // https://docs.microsoft.com/en-us/microsoftteams/platform/webhooks-and-connectors/how-to/connectors-using

  private static String MESSAGE_CARD_CONTEXT = "http://schema.org/extensions";
  private static String MESSAGE_CARD_TITLE = "Spinnaker Notification";
  private static String MESSAGE_CARD_TYPE = "MessageCard";

  @JsonProperty("@context")
  public String context;

  @JsonProperty("@type")
  public String type;

  public String correlationId;
  public List<MicrosoftTeamsSection> sections;
  public String summary;
  public String themeColor;
  public String title;

  private transient String customMessage;
  private transient String status;

  public MicrosoftTeamsMessage(String summary, String status) {
    this.context = MESSAGE_CARD_CONTEXT;
    this.correlationId = this.createRandomUUID();
    this.status = status;
    this.summary = summary;
    this.title = MESSAGE_CARD_TITLE;
    this.type = MESSAGE_CARD_TYPE;

    sections = new ArrayList<>();
    themeColor = this.getThemeColor(status);
  }

  @JsonIgnore
  public MicrosoftTeamsSection createSection(String executionType, String title) {
    return new MicrosoftTeamsSection(executionType, title);
  }

  @JsonIgnore
  public void addSection(MicrosoftTeamsSection section) {
    sections.add(section);
  }

  private String getThemeColor(String status) {
    String color = "";
    status = (status == null) ? "" : status;

    if (status.contains("failed")) {
      color = "EB1A1A";
    } else if (status.contains("complete")) {
      color = "73DB69";
    } else {
      color = "0076D7";
    }

    return color;
  }

  private String createRandomUUID() {
    return UUID.randomUUID().toString();
  }
}
