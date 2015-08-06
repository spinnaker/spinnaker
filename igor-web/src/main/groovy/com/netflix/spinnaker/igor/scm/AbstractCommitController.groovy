/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.scm

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.rest.webmvc.ResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

import java.util.concurrent.ExecutorService

@Slf4j
@RestController
abstract class AbstractCommitController {
    @Autowired
    ExecutorService executor

    @Autowired
    ObjectMapper objectMapper

    @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "toCommit and fromCommit parameters are required in the query string")
    @InheritConstructors
    static class MissingParametersException extends RuntimeException {}

    @RequestMapping(value = '/{projectKey}/{repositorySlug}/compareCommits')
    List compareCommits(@PathVariable(value = 'projectKey') String projectKey, @PathVariable(value='repositorySlug') repositorySlug, @RequestParam Map<String, String> requestParams) {
        if(!requestParams.to || !requestParams.from) {
            throw new MissingParametersException()
        }
    }

    List getNotFoundCommitsResponse(String projectKey, String repositorySlug, String to, String from, String url) {
        return [[displayId: "NOT_FOUND", id: "NOT_FOUND", authorDisplayName: "UNKNOWN",
                   timestamp: 0, message : "could not find any commits from ${from} to ${to} in ${url} ${projectKey}/${repositorySlug}".toString(), commitUrl: url]]
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Could not contact the server")
    public void handleRuntimeException(RuntimeException ex)
    {
        log.error("Could not contact the server", ex)
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Could not find either project|repo|to commit|from commit")
    public void handleResourceNotFoundException(ResourceNotFoundException ex)
    {
        log.error("Could not find either project|repo|to commit|from commit", ex)
    }
}
