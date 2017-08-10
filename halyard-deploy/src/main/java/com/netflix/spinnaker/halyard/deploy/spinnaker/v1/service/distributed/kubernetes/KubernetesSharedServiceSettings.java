package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.SharedDistributedServiceSettings;
import org.apache.commons.lang3.StringUtils;

public class KubernetesSharedServiceSettings extends SharedDistributedServiceSettings {

    public KubernetesSharedServiceSettings(DeploymentConfiguration deploymentConfiguration) {
        super(deploymentConfiguration);
    }

    @Override
    public String getDeployLocation() {
        return StringUtils.isEmpty(deploymentConfiguration.getDeploymentEnvironment().getLocation())
                ? "spinnaker"
                : deploymentConfiguration.getDeploymentEnvironment().getLocation();
    }
}
