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

package com.netflix.spinnaker.echo.microsoftteams.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.echo.api.Notification.InteractiveActions;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class MicrosoftTeamsSection {
  public String activityTitle;
  public List<MicrosoftTeamsFact> facts;
  public List<MicrosoftTeamsPotentialAction> potentialAction;

  private transient String executionType;

  public MicrosoftTeamsSection(String executionType, String title) {
    this.activityTitle = title.trim();
    this.facts = new ArrayList<>();
    this.potentialAction = new ArrayList<>();

    if (executionType == null) {
      this.executionType = "";
    } else {
      this.executionType = executionType.trim();
    }
  }

  @JsonIgnore
  public void setApplicationName(String applicationName) {
    if (!StringUtils.isEmpty(applicationName)) {
      facts.add(new MicrosoftTeamsFact("Application Name", applicationName));
    }
  }

  @JsonIgnore
  public void setCustomMessage(String message) {
    if (!StringUtils.isEmpty(message)) {
      facts.add(new MicrosoftTeamsFact("Custom Message", message));
    }
  }

  @JsonIgnore
  public void setDescription(String description) {
    if (!StringUtils.isEmpty(description)) {
      facts.add(new MicrosoftTeamsFact("Description", description));
    }
  }

  @JsonIgnore
  public void setExecutionName(String executionName) {
    if (!StringUtils.isEmpty(executionName)) {
      facts.add(new MicrosoftTeamsFact("Execution Name", executionName));
    }
  }

  @JsonIgnore
  public void setEventName(String eventName) {
    if (!StringUtils.isEmpty(eventName)) {
      switch (executionType.toLowerCase()) {
        case "pipeline":
          facts.add(new MicrosoftTeamsFact("Pipeline Name", eventName));
          break;
        case "stage":
          facts.add(new MicrosoftTeamsFact("Stage Name", eventName));
          break;
        default:
          facts.add(new MicrosoftTeamsFact("Event Name", eventName));
      }
    }
  }

  @JsonIgnore
  public void setMessage(String message) {
    if (!StringUtils.isEmpty(message)) {
      facts.add(new MicrosoftTeamsFact("Message", message));
    }
  }

  @JsonIgnore
  public void setStatus(String status) {
    if (!StringUtils.isEmpty(status)) {
      status = status.substring(0, 1).toUpperCase() + status.substring(1);
      facts.add(new MicrosoftTeamsFact("Status", status));
    }
  }

  @JsonIgnore
  public void setSummary(String summary) {
    if (!StringUtils.isEmpty(summary)) {
      facts.add(new MicrosoftTeamsFact("Summary", summary));
    }
  }

  @JsonIgnore
  public void setPotentialAction(String url, InteractiveActions interactiveActions) {
    MicrosoftTeamsPotentialAction action = new MicrosoftTeamsPotentialAction();

    if (interactiveActions != null) {
      log.debug("Setting multi choice actions");
      action.setMultiChoiceAction(interactiveActions.getActions());
    } else {
      if (url != null) {
        log.debug("Setting single choice action using url " + url);
        action.setSingleChoiceAction(url);
        potentialAction.add(action);
      }
    }
  }
}
