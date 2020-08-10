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
package com.netflix.spinnaker.front50.controllers;

import com.google.common.base.Strings;
import com.netflix.spinnaker.front50.config.annotations.ConditionalOnAnyProviderExceptRedisIsEnabled;
import com.netflix.spinnaker.front50.exceptions.InvalidEntityException;
import com.netflix.spinnaker.front50.model.snapshot.Snapshot;
import com.netflix.spinnaker.front50.model.snapshot.SnapshotDAO;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.Collection;
import java.util.Objects;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/snapshots")
@ConditionalOnAnyProviderExceptRedisIsEnabled
public class SnapshotsController {

  private final SnapshotDAO snapshotDAO;

  public SnapshotsController(SnapshotDAO snapshotDAO) {
    this.snapshotDAO = snapshotDAO;
  }

  @PreAuthorize("@fiatPermissionEvaluator.storeWholePermission()")
  @PostFilter("hasPermission(filterObject.application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/{id:.+}/history", method = RequestMethod.GET)
  public Collection<Snapshot> getHistory(
      @PathVariable String id, @RequestParam(value = "limit", defaultValue = "20") int limit) {
    return snapshotDAO.history(id, limit);
  }

  @PostAuthorize("hasPermission(returnObject.application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/{id:.+}", method = RequestMethod.GET)
  public Snapshot getCurrent(@PathVariable String id) {
    return snapshotDAO.findById(id);
  }

  @PostAuthorize("hasPermission(returnObject.application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/{id:.+}/{timestamp:.+}", method = RequestMethod.GET)
  public Snapshot getVersionByTimestamp(
      @PathVariable String id,
      @PathVariable String timestamp,
      @RequestParam(value = "limit", defaultValue = "20") int limit) {
    final Long creationTime = Long.parseLong(timestamp);
    return snapshotDAO.history(id, limit).stream()
        .filter(it -> Objects.equals(it.getTimestamp(), creationTime))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Snapshot not found"));
  }

  @PreAuthorize("hasPermission(#snapshot.application, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "", method = RequestMethod.POST)
  public void save(@RequestBody Snapshot snapshot) {
    if (Strings.isNullOrEmpty(snapshot.getApplication())
        || Strings.isNullOrEmpty(snapshot.getAccount())) {
      throw new InvalidEntityException("A snapshot requires application and account fields");
    }
    String id = snapshot.getApplication() + "-" + snapshot.getAccount();
    snapshotDAO.create(id, snapshot);
  }
}
