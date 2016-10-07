package org.openstack4j.model.heat.builder;

/**
 * TODO remove once openstack4j 3.0.3 is released
 * The Orchestration builders
 */
public interface OrchestrationBuilders {

    /**
     * The builder to create a Template
     *
     * @return the TemplateBuilder
     */
    public TemplateBuilder template();

    /**
     * The builder to create a StackCreate
     *
     * @return the StackCreate builder
     */
    public StackCreateBuilder stack();

    /**
     * The builder to create a SoftwareConfig
     *
     * @return the software config builder
     */
    public SoftwareConfigBuilder softwareConfig();

    /**
     * The builder to create a StackUpdate
     *
     * @return the StackUpdate builder
     */
    public StackUpdateBuilder stackUpdate();

    /**
     * The builder to create a resource health update
     *
     * @return
     */
    public ResourceHealthBuilder resourceHealth();

}
