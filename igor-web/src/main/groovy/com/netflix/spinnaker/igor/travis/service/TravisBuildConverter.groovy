/*
 * Copyright 2016 Schibsted ASA.
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

package com.netflix.spinnaker.igor.travis.service

import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.travis.client.model.Build
import com.netflix.spinnaker.igor.travis.client.model.Repo
import groovy.transform.CompileStatic

@CompileStatic
class TravisBuildConverter {
    static GenericBuild genericBuild(Build build, String repoSlug, String baseUrl) {
        GenericBuild genericBuild = new GenericBuild(build.state == 'started', build.number, build.duration, TravisResultConverter.getResultFromTravisState(build.state), repoSlug, url(repoSlug, baseUrl, build.id))
        if (build.finishedAt) {
            genericBuild.timestamp = build.timestamp()
        }
        return genericBuild
    }

    static GenericBuild genericBuild(Repo repo, String baseUrl) {
        GenericBuild genericBuild = new GenericBuild((repo.lastBuildState == 'started'), repo.lastBuildNumber, repo.lastBuildDuration, TravisResultConverter.getResultFromTravisState(repo.lastBuildState), repo.slug, url(repo.slug, baseUrl, repo.lastBuildId))
        if (repo.lastBuildFinishedAt) {
            genericBuild.timestamp = repo.timestamp()
        }
        return genericBuild
    }

    static String url(String repoSlug, String baseUrl, int id) {
        "${baseUrl}/${repoSlug}/builds/${id}"
    }
}
