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

import com.netflix.spinnaker.igor.scm.github.client.GitHubClient
import com.netflix.spinnaker.igor.scm.github.client.GitHubMaster
import com.netflix.spinnaker.igor.scm.stash.client.StashClient
import com.netflix.spinnaker.igor.scm.stash.client.StashMaster
import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketClient
import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketMaster
import spock.lang.Specification
import spock.lang.Subject

/**
 * Tests for InfoController
 */
class ScmInfoControllerSpec extends Specification {

    @Subject
    ScmInfoController controller

    StashClient stashClient = Mock(StashClient)
    GitHubClient gitHubClient = Mock(GitHubClient)
    BitBucketClient bitBucketClient = Mock(BitBucketClient)

    void setup() {
        controller = new ScmInfoController(gitHubMaster: new GitHubMaster(gitHubClient: gitHubClient, baseUrl: "https://github.com"),
                                           stashMaster: new StashMaster(stashClient: stashClient, baseUrl: "http://stash.com"),
                                           bitBucketMaster: new BitBucketMaster(bitBucketClient: bitBucketClient, baseUrl: "https://api.bitbucket.org"))
    }

    void 'list masters'() {
        when:
        Map listMastersResponse = controller.listMasters()

        then:
        listMastersResponse.stash == "http://stash.com"
        listMastersResponse.gitHub == "https://github.com"
        listMastersResponse.bitBucket == "https://api.bitbucket.org"
    }
}
