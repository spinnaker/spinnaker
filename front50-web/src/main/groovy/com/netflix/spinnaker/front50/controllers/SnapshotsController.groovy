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

package com.netflix.spinnaker.front50.controllers

import com.netflix.spinnaker.front50.exceptions.InvalidEntityException
import com.netflix.spinnaker.front50.model.snapshot.Snapshot
import com.netflix.spinnaker.front50.model.snapshot.SnapshotDAO
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/snapshots")
@ConditionalOnExpression('${spinnaker.gcs.enabled:false} || ${spinnaker.s3.enabled:false} || ${spinnaker.azs.enabled:false} || ${spinnaker.oracle.enabled:false}')
class SnapshotsController {

    @Autowired
    SnapshotDAO snapshotDAO

    @PreAuthorize("@fiatPermissionEvaluator.storeWholePermission()")
    @PostFilter("hasPermission(filterObject.application, 'APPLICATION', 'READ')")
    @RequestMapping(value = "/{id:.+}/history", method = RequestMethod.GET)
    Collection<Snapshot> getHistory(@PathVariable String id,
                                    @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return snapshotDAO.history(id, limit)
    }

    @PostAuthorize("hasPermission(returnObject.application, 'APPLICATION', 'READ')")
    @RequestMapping(value = "/{id:.+}", method = RequestMethod.GET)
    Snapshot getCurrent(@PathVariable String id) {
        return snapshotDAO.findById(id)
    }

    @PostAuthorize("hasPermission(returnObject.application, 'APPLICATION', 'READ')")
    @RequestMapping(value = "/{id:.+}/{timestamp:.+}", method = RequestMethod.GET)
    Snapshot getVersionByTimestamp(@PathVariable String id,
                                   @PathVariable String timestamp,
                                   @RequestParam(value = "limit", defaultValue = "20") int limit) {
        def creationTime = timestamp.toLong()
        snapshotDAO.history(id, limit).find { Snapshot snapshot ->
            snapshot.timestamp == creationTime
        }
    }

    @PreAuthorize("hasPermission(#snapshot.application, 'APPLICATION', 'WRITE')")
    @RequestMapping(value = "", method = RequestMethod.POST)
    void save(@RequestBody Snapshot snapshot) {
        if (!snapshot.application || !snapshot.account) {
            throw new InvalidEntityException("A snapshot requires application and account fields")
        }
        String id = "$snapshot.application-$snapshot.account"
        snapshotDAO.create(id, snapshot)
    }
}
