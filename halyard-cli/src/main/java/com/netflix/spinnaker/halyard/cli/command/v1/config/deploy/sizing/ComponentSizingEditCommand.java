/*
 * Copyright 2018 Google, Inc.
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
 *
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.deploy.sizing;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.ha.HaServices;
import com.netflix.spinnaker.halyard.config.model.v1.node.CustomSizing;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Parameters(separators = "=")
public class ComponentSizingEditCommand extends AbstractComponentSizingUpdateCommand {

    @Parameter(
            names = "--replicas",
            description = "Set the number of replicas (pods) to be created for this service."
    )
    private Integer replicas = 1;

    @Parameter(
            names = "--pod-requests-cpu",
            description = "Sets the cpu request for the container running the spinnaker service, as well as any " +
                    "sidecar containers (e.g. the monitoring daemon). Example: 250m."
    )
    private String requestsCpu;

    @Parameter(
            names = "--pod-requests-memory",
            description = "Sets the memory request for the container running the spinnaker service, as well as any " +
                    "sidecar containers (e.g. the monitoring daemon). Example: 512Mi."
    )
    private String requestsMemory;

    @Parameter(
            names = "--pod-limits-cpu",
            description = "Sets the cpu limit for the container running the spinnaker service, as well as any " +
                    "sidecar containers (e.g. the monitoring daemon). Example: 1."
    )
    private String limitsCpu;

    @Parameter(
            names = "--pod-limits-memory",
            description = "Sets the memory limit for the container running the spinnaker service, as well as any " +
                    "sidecar containers (e.g. the monitoring daemon). Example: 1Gi."
    )
    private String limitsMemory;

    public ComponentSizingEditCommand(SpinnakerService.Type spinnakerService) {
        super(spinnakerService, "edit");
    }

    @Override
    protected String getShortDescription() {
        return "Edit the component sizing for service " + spinnakerService.getCanonicalName() +
                ", such as the number of replicas and the resources limits.";
    }

    @Override
    protected CustomSizing update(CustomSizing customSizing) {
        return edit(customSizing);
    }

    private CustomSizing edit(CustomSizing customSizing) {
        Map serviceSizing = customSizing.computeIfAbsent(spinnakerService.getServiceName(), (k) -> new HashMap<>());
        edit(serviceSizing);
        return customSizing;
    }

    private void edit(Map serviceSizing) {
        putIfNotNull(serviceSizing, "replicas", replicas);

        Map limits = (Map) serviceSizing.computeIfAbsent("limits", (k) -> new HashMap<>());
        putIfNotNull(limits, "cpu", limitsCpu);
        putIfNotNull(limits, "memory", limitsMemory);

        Map requests = (Map) serviceSizing.computeIfAbsent("requests", (k) -> new HashMap<>());
        putIfNotNull(requests, "cpu", requestsCpu);
        putIfNotNull(requests, "memory", requestsMemory);
    }

    private void putIfNotNull(Map map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
