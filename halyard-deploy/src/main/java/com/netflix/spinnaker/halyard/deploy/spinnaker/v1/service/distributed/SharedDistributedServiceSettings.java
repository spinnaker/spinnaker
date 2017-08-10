package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;

abstract public class SharedDistributedServiceSettings {

    protected DeploymentConfiguration deploymentConfiguration;

    public SharedDistributedServiceSettings(DeploymentConfiguration deploymentConfiguration) {
        this.deploymentConfiguration = deploymentConfiguration;
    }

    abstract public String getDeployLocation();
}
