/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.igor.admin;

import static java.lang.String.format;

import com.netflix.spinnaker.igor.polling.CommonPollingMonitor;
import com.netflix.spinnaker.igor.polling.PollingMonitor;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
public class AdminController {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final List<CommonPollingMonitor> pollingMonitors;

  @Autowired
  public AdminController(Optional<List<CommonPollingMonitor>> pollingMonitors) {
    this.pollingMonitors = pollingMonitors.orElseGet(ArrayList::new);
  }

  /**
   * Silently fast-forwards a poller. Fast-forwarding means that all pending cache state will be
   * polled and saved, but will not send Echo notifications.
   *
   * <p>By default all partitions (ex: masters) will be fast-forwarded, however specific partition
   * names should be used whenever possible.
   *
   * @param monitorName The polling monitor name (ex: "DockerMonitor")
   * @param partition The partition name, if not provided, method will re-index the entire monitor
   */
  @RequestMapping(value = "/pollers/fastforward/{monitorName}", method = RequestMethod.POST)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void fastForward(
      @PathVariable String monitorName, @RequestParam(required = false) String partition) {
    CommonPollingMonitor pollingMonitor =
        pollingMonitors.stream()
            .filter(it -> it.getName().equals(monitorName))
            .findFirst()
            .orElseThrow(
                () ->
                    new NotFoundException(
                        format(
                            "PollingMonitor %s was not found, available monitors are: %s",
                            monitorName,
                            pollingMonitors.stream()
                                .map(PollingMonitor::getName)
                                .collect(Collectors.toList()))));

    log.warn("Re-indexing {}:{}", monitorName, (partition == null) ? "ALL" : partition);
    if (partition == null) {
      pollingMonitor.poll(false);
    } else {
      pollingMonitor.pollSingle(pollingMonitor.getPollContext(partition).fastForward());
    }
  }
}
