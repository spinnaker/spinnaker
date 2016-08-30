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

import com.netflix.spinnaker.front50.model.snapshot.Snapshot
import com.netflix.spinnaker.front50.model.snapshot.SnapshotDAO
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY

@RestController
@RequestMapping("/snapshots")
@ConditionalOnExpression('${spinnaker.gcs.enabled:false} || ${spinnaker.s3.enabled:false}')
class SnapshotsController {

    @Autowired
    SnapshotDAO snapshotDAO

    @PostFilter("hasPermission(filterObject.application, 'APPLICATION', 'READ')")
    @RequestMapping(value = "/{id:.+}/history", method = RequestMethod.GET)
    Collection<Snapshot> getHistory(@PathVariable String id,
                                    @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return snapshotDAO.getHistory(id, limit)
    }

    @PostAuthorize("hasPermission(returnObject.application, 'APPLICATION', 'READ')")
    @RequestMapping(value = "/{id:.+}", method = RequestMethod.GET)
    Snapshot getCurrent(@PathVariable String id) {
        return snapshotDAO.findById(id)
    }

    @PreAuthorize("hasPermission(#snapshot.application, 'APPLICATION', 'WRITE')")
    @RequestMapping(value = "", method = RequestMethod.POST)
    void save(@RequestBody Snapshot snapshot) {
        if (!snapshot.application || !snapshot.account) {
            throw new InvalidSnapshotDefinition()
        }
        String id = "$snapshot.application-$snapshot.account"
        snapshotDAO.create(id, snapshot)
    }

    @ExceptionHandler(InvalidSnapshotDefinition)
    @ResponseStatus(UNPROCESSABLE_ENTITY)
    Map handleInvalidSnapshotDefinition() {
        return [error: "A snapshot requires application and account fields", status: UNPROCESSABLE_ENTITY]
    }

    @ExceptionHandler(AccessDeniedException)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    Map handleAccessDeniedException(AccessDeniedException ade) {
        return [error: "Access is denied", status: HttpStatus.FORBIDDEN.value()]
    }

    static class InvalidSnapshotDefinition extends Exception {}
}
