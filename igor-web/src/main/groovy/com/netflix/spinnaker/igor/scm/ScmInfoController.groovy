/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.scm

import com.netflix.spinnaker.igor.scm.github.client.GitHubMaster
import com.netflix.spinnaker.igor.scm.stash.client.StashMaster
import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketMaster
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

/**
 * A controller that provides Source Control information
 */
@RestController
@Slf4j
@RequestMapping("/scm")
class ScmInfoController {
    @Autowired(required = false)
    StashMaster stashMaster

    @Autowired(required = false)
    GitHubMaster gitHubMaster

    @Autowired(required = false)
    BitBucketMaster bitBucketMaster

    @RequestMapping(value = '/masters', method = RequestMethod.GET)
    Map listMasters() {
        log.info('Getting list of masters')
        def result = [:]
        if(stashMaster)
            result << [stash : stashMaster.baseUrl]

        if(gitHubMaster)
            result << [gitHub : gitHubMaster.baseUrl]

        if(bitBucketMaster)
            result << [bitBucket : bitBucketMaster.baseUrl]
        result
    }
}
