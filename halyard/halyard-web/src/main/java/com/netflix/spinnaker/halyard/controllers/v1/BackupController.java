/*
 * Copyright 2017 Google, Inc.
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
 *
 *
 */

package com.netflix.spinnaker.halyard.controllers.v1;

import com.netflix.spinnaker.halyard.backup.services.v1.BackupService;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.core.DaemonResponse.StaticRequestBuilder;
import com.netflix.spinnaker.halyard.core.StringBodyRequest;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reports the entire contents of ~/.hal/config */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/backup")
public class BackupController {
  private final BackupService backupService;

  @RequestMapping(value = "/create", method = RequestMethod.PUT)
  DaemonTask<Halconfig, StringBodyRequest> create() {
    StaticRequestBuilder<StringBodyRequest> builder =
        new StaticRequestBuilder<>(() -> new StringBodyRequest(backupService.create()));
    return DaemonTaskHandler.submitTask(builder::build, "Create backup");
  }

  @RequestMapping(value = "/restore", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> restore(@RequestParam String backupPath) {
    StaticRequestBuilder<Void> builder =
        new StaticRequestBuilder<>(
            () -> {
              backupService.restore(backupPath);
              return null;
            });
    return DaemonTaskHandler.submitTask(builder::build, "Restore backup");
  }
}
