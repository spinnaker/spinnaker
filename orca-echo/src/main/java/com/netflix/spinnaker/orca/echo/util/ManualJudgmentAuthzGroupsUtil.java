/*
 * Copyright 2020 OpsMx, Inc.
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

package com.netflix.spinnaker.orca.echo.util;

import static java.lang.String.format;

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.Application;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import retrofit.RetrofitError;

public class ManualJudgmentAuthzGroupsUtil {

  Front50Service front50Service;

  @Autowired
  public ManualJudgmentAuthzGroupsUtil(Optional<Front50Service> front50Service) {
    this.front50Service = front50Service.orElse(null);
  }

  /**
   * This method checks if the logged in user has role in the manual judgment stage authorized
   * groups. We fetch the user roles and check if that role is authorized in the manual judgment
   * stage role. if the user role exists , then we check with the application permission roles. If
   * the application permission role has 'READ' then we return false(not authorized) If the
   * application permission role has 'CREATE, EXECUTE, WRITE' then we return true(authorized)
   *
   * @param userRoles
   * @param stageRoles
   * @param permissions
   * @return
   */
  public static boolean checkAuthorizedGroups(
      List<String> userRoles, List<String> stageRoles, Map<String, Object> permissions) {

    boolean isAuthorizedGroup = false;
    if (stageRoles == null || stageRoles.isEmpty()) {
      return true;
    }
    for (String role : userRoles) { // Fetches the userRoles of the logged in user
      if (stageRoles.contains(
          role)) { // Checks if the user role is authorized in the manual judgment stage.
        for (Map.Entry<String, Object> entry :
            permissions.entrySet()) { // get the application permission roles.
          if (Authorization.CREATE.name().equals(entry.getKey())
              || Authorization.EXECUTE.name().equals(entry.getKey())
              || Authorization.WRITE.name().equals(entry.getKey())) {
            // If the application permission roles has 'CREATE, EXECUTE, WRITE', then user is
            // authorized.
            if (entry.getValue() != null && ((List<String>) entry.getValue()).contains(role)) {
              return true;
            }
          } else if (Authorization.READ.name().equals(entry.getKey())) {
            // If the application permission roles has 'READ', then user is not authorized.
            if (entry.getValue() != null && ((List<String>) entry.getValue()).contains(role)) {
              isAuthorizedGroup = false;
            }
          }
        }
      }
    }
    return isAuthorizedGroup;
  }

  public Optional<Application> getApplication(String applicationName) {
    try {
      return Optional.of(front50Service.get(applicationName));
    } catch (RetrofitError e) {
      if (e.getResponse().getStatus() == HttpStatus.NOT_FOUND.value()) {
        return Optional.empty();
      }
      throw new SpinnakerException(
          format("Failed to retrieve application '%s'", applicationName), e);
    } catch (RuntimeException re) {
      return Optional.empty();
    }
  }
}
