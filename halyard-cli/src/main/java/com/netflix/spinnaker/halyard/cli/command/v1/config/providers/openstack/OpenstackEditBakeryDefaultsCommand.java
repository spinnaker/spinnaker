/*
 * Copyright 2017 Target, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.openstack;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.config.model.v1.providers.openstack.OpenstackBakeryDefaults;
import com.netflix.spinnaker.halyard.config.model.v1.node.BakeryDefaults;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.AbstractEditBakeryDefaultsCommand;

/**
 * Interact with openstack provider's bakery
 */
@Parameters(separators = "=")
public class OpenstackEditBakeryDefaultsCommand extends AbstractEditBakeryDefaultsCommand<OpenstackBakeryDefaults> {
    protected String getProviderName() {
        return "openstack";
    }

    @Parameter(
        names = "--auth-url",
        required = true,
        description = "Set the default auth URL your images will be baked in."
    )
    private String authUrl;

    @Parameter(
        names = "--domain-name",
        required = true,
        description = "Set the default domainName your images will be baked in."
    )
    private String domainName;

    @Parameter(
        names = "--network-id",
        required = true,
        description = "Set the default network your images will be baked in."
    )
    private String networkId;

    @Parameter(
        names = "--floating-ip-pool",
        required = true,
        description = "Set the default floating IP pool your images will be baked in."
    )
    private String floatingIpPool;

    @Parameter(
        names = "--security-groups",
        required = true,
        description = "Set the default security group your images will be baked in."
    )
    private String securityGroups;

    @Parameter(
        names = "--project-name",
        required = true,
        description = "Set the default project name your images will be baked in."
    )
    private String projectName;

    @Parameter(
        names = "--username",
        required = true,
        description = "Set the default username your images will be baked with."
    )
    private String username;

    @Parameter(
        names = "--password",
        required = true,
        description = "Set the default password your images will be baked with."
    )
    private String password;

    @Parameter(
        names = "--insecure",
        required = true,
        arity = 1,
        description = "The security setting (true/false) for connecting to the Openstack account."
    )
    private Boolean insecure;

    @Override
    protected BakeryDefaults editBakeryDefaults(OpenstackBakeryDefaults bakeryDefaults) {
        bakeryDefaults.setAuthUrl(isSet(authUrl) ? authUrl : bakeryDefaults.getAuthUrl());
        bakeryDefaults.setDomainName(isSet(domainName) ? domainName : bakeryDefaults.getDomainName());
        bakeryDefaults.setNetworkId(isSet(networkId) ? networkId : bakeryDefaults.getNetworkId());
        bakeryDefaults.setFloatingIpPool(isSet(floatingIpPool) ? floatingIpPool : bakeryDefaults.getFloatingIpPool());
        bakeryDefaults.setSecurityGroups(isSet(securityGroups) ? securityGroups : bakeryDefaults.getSecurityGroups());
        bakeryDefaults.setProjectName(isSet(projectName) ? projectName : bakeryDefaults.getProjectName());
        bakeryDefaults.setUsername(isSet(username) ? username : bakeryDefaults.getUsername());
        bakeryDefaults.setPassword(isSet(password) ? password : bakeryDefaults.getPassword());
        bakeryDefaults.setInsecure(isSet(insecure) ? insecure : bakeryDefaults.getInsecure());

        return bakeryDefaults;
    }

}
