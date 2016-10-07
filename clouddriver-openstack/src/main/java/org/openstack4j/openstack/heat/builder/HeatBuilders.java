package org.openstack4j.openstack.heat.builder;

import org.openstack4j.model.heat.builder.OrchestrationBuilders;
import org.openstack4j.model.heat.builder.ResourceHealthBuilder;
import org.openstack4j.model.heat.builder.SoftwareConfigBuilder;
import org.openstack4j.model.heat.builder.StackCreateBuilder;
import org.openstack4j.model.heat.builder.StackUpdateBuilder;
import org.openstack4j.model.heat.builder.TemplateBuilder;
import org.openstack4j.openstack.heat.domain.HeatResourceHealth;
import org.openstack4j.openstack.heat.domain.HeatSoftwareConfig;
import org.openstack4j.openstack.heat.domain.HeatStackCreate;
import org.openstack4j.openstack.heat.domain.HeatStackUpdate;
import org.openstack4j.openstack.heat.domain.HeatTemplate;

/**
 * TODO remove once openstack4j 3.0.3 is released
 * The Orchestration V3 Builders
 */
public class HeatBuilders implements OrchestrationBuilders {

    private OrchestrationBuilders HeatBuilders() {
        return this;
    }

    @Override
    public TemplateBuilder template() {
        return HeatTemplate.build();
    }

    @Override
    public StackCreateBuilder stack() {
        return HeatStackCreate.build();
    }

    @Override
    public SoftwareConfigBuilder softwareConfig() {
        return new HeatSoftwareConfig.Builder();
    }

    @Override
    public StackUpdateBuilder stackUpdate() {
        return HeatStackUpdate.builder();
    }

    @Override
    public ResourceHealthBuilder resourceHealth() {
        return HeatResourceHealth.builder();
    }
}
