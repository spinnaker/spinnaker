/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.fiat.roles;

import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import com.netflix.spinnaker.fiat.permissions.PermissionsResolver;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserRolesSyncer {

  @Autowired
  @Setter
  private PermissionsRepository permissionsRepository;

  @Autowired
  @Setter
  private PermissionsResolver permissionsResolver;

  // TODO(ttomsu): Acquire a lock in order to make this scale to multiple instances.
  @Scheduled(initialDelay = 10000L, fixedRate = 600000L)
  public void sync() {
    log.info("Starting user role sync.");
    val permissionMap = permissionsRepository.getAllById();

    permissionsResolver.resolve(permissionMap.keySet())
                       .entrySet()
                       .stream()
                       .forEach(entry -> permissionsRepository.put(entry.getKey(), entry.getValue()));
    log.info("Synced " + permissionMap.keySet().size() + " user roles.");
  }
}
