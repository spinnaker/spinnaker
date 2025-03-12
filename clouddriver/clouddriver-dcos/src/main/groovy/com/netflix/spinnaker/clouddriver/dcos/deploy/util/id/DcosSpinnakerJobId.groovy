/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.deploy.util.id

import com.netflix.frigga.Names

/**
 * Simple wrapper that conforms to frigga to ensure it works with the job operations within Orca but adds DC/OS specific
 * meaning/context to parts to ensure we have all necessary data to get job info from within DC/OS if necessary.
 */
class DcosSpinnakerJobId {
    private Names names

    public DcosSpinnakerJobId(String id) {
        names = Names.parseName(id)
    }

    public DcosSpinnakerJobId(String app, String job, String task) {
        names = Names.parseName("${app}-${job}-${task}".toString())
    }

    public String getAppName() {
        names.app
    }

    public String getJobName() {
        names.stack
    }

    public String getTaskName() {
        names.detail
    }

    public String getMesosTaskName() {
        "${names.detail}.${names.stack}".toString()
    }

    @Override
    public String toString() {
        names.group
    }
}
