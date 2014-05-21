/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.kork.eureka;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.LookupService;

import java.util.ArrayList;
import java.util.List;

public class StaticLookupService implements LookupService {

    private final Applications applications;

    public StaticLookupService(Applications applications) {
        this.applications = applications;
    }

    @Override
    public Application getApplication(String appName) {
        return applications.getRegisteredApplications(appName);
    }

    @Override
    public Applications getApplications() {
        return applications;
    }

    @Override
    public List<InstanceInfo> getInstancesById(String id) {
        List<InstanceInfo> instancesList = new ArrayList<InstanceInfo>();
        for (Application app : this.getApplications()
                .getRegisteredApplications()) {
            InstanceInfo instanceInfo = app.getByInstanceId(id);
            if (instanceInfo != null) {
                instancesList.add(instanceInfo);
            }
        }
        return instancesList;
    }

    @Override
    public InstanceInfo getNextServerFromEureka(String virtualHostname, boolean secure) {
        List<InstanceInfo> instances = secure ?
                applications.getInstancesBySecureVirtualHostName(virtualHostname) :
                applications.getInstancesByVirtualHostName(virtualHostname);
        if (instances == null || instances.isEmpty()) {
            throw new RuntimeException("No matches for the virtual host name :"
                    + virtualHostname);
        }

        int index = (int) (applications.getNextIndex(virtualHostname.toUpperCase(),
                secure).incrementAndGet() % instances.size());

        return instances.get(index);
    }
}
