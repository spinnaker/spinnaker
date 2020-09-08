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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.echo.api.Notification.InteractiveAction;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicrosoftTeamsPotentialAction {
  private static String ACTION_NAME_SINGLE_ACTION = "View Execution";
  private static String ACTION_NAME_MULTI_ACTION = "Choose an option below";
  private static String ACTION_TYPE_OPEN_URI = "OpenUri";
  private static String ACTION_TYPE_MULTI_CHOICE_INPUT = "ActionCard";

  @JsonProperty("@type")
  public String type;

  public String name;
  public List<MicrosoftTeamsPotentialActionTarget> targets;
  public boolean isMultiSelect = false;
  public List<MicrosoftTeamsPotentialActionChoice> actions;

  public MicrosoftTeamsPotentialAction() {
    this.name = ACTION_NAME_SINGLE_ACTION;
  }

  @JsonIgnore
  public void setMultiChoiceAction(List<InteractiveAction> interactiveActions) {
    log.debug("Setting Teams multi choice action");
    name = ACTION_NAME_MULTI_ACTION;
    type = ACTION_TYPE_MULTI_CHOICE_INPUT;
    actions = new ArrayList<>();

    for (InteractiveAction action : interactiveActions) {
      actions.add(new MicrosoftTeamsPotentialActionChoice(action.getName(), action.getValue()));
    }
  }

  @JsonIgnore
  public void setSingleChoiceAction(String url) {
    log.debug("Setting Teams single choice action");
    targets = new ArrayList<>();
    type = ACTION_TYPE_OPEN_URI;

    targets.add(new MicrosoftTeamsPotentialActionTarget(url));
  }
}
