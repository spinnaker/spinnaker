/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.gate.config

import com.netflix.zuul.FilterFileManager

class FilterFileManagerConfigurer {
    private final int pollIntervalSeconds

    private final File preFilterRoot

    private final File routeFilterRoot

    private final File postFilterRoot

    private final FilenameFilter zuulFilenameFilter

    FilterFileManagerConfigurer(int pollIntervalSeconds, File preFilterRoot, File routeFilterRoot, File postFilterRoot, FilenameFilter zuulFilenameFilter) {
        this.pollIntervalSeconds = pollIntervalSeconds
        this.preFilterRoot = preFilterRoot
        this.routeFilterRoot = routeFilterRoot
        this.postFilterRoot = postFilterRoot
        this.zuulFilenameFilter = zuulFilenameFilter
    }

    protected FilterFileManager createInstance() throws Exception {
        FilterFileManager.filenameFilter = zuulFilenameFilter
        FilterFileManager.init(pollIntervalSeconds, preFilterRoot.canonicalPath, routeFilterRoot.canonicalPath, postFilterRoot.canonicalPath)
        FilterFileManager.getInstance()
    }

    FilterFileManager getFilterFileManager() {
        FilterFileManager.getInstance()
    }
}
