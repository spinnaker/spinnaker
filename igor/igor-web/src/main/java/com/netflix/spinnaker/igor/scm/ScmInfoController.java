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

package com.netflix.spinnaker.igor.scm;

import com.netflix.spinnaker.igor.scm.github.client.GitHubMaster;
import com.netflix.spinnaker.igor.scm.gitlab.client.GitLabMaster;
import com.netflix.spinnaker.igor.scm.stash.client.StashMaster;
import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketMaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * A controller that provides Source Control information
 */
@RestController
@Slf4j
@RequestMapping("/scm")
public class ScmInfoController {
    @Autowired(required = false)
    private StashMaster stashMaster;

    @Autowired(required = false)
    private GitHubMaster gitHubMaster;

    @Autowired(required = false)
    private GitLabMaster gitLabMaster;

    @Autowired(required = false)
    private BitBucketMaster bitBucketMaster;

    @RequestMapping(value = "/masters", method = RequestMethod.GET)
    public Map<String, String> listMasters() {
        Map<String, String> result = new HashMap<>();
        if (stashMaster != null) {
            result.put("stash", stashMaster.getBaseUrl());
        }

        if (gitHubMaster != null) {
            result.put("gitHub", gitHubMaster.getBaseUrl());
        }

        if (gitLabMaster != null) {
            result.put("gitLab", gitLabMaster.getBaseUrl());
        }

        if (bitBucketMaster != null) {
            result.put("bitBucket", bitBucketMaster.getBaseUrl());
        }
        return result;
    }
}
