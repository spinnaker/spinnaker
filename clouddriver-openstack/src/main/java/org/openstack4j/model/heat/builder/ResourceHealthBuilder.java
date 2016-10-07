package org.openstack4j.model.heat.builder;

import org.openstack4j.common.Buildable;
import org.openstack4j.model.heat.ResourceHealth;

/**
 * TODO remove once openstack4j 3.0.3 is released
 * This interface describes a builder for {@link ResourceHealth} objects
 *
 * @author Dan Maas
 */
public interface ResourceHealthBuilder extends Buildable.Builder<ResourceHealthBuilder, ResourceHealth> {

    /**
     * Set the unhealthy status of the resource.
     *
     * @param markUnhealthy
     * @return
     */
    ResourceHealthBuilder markUnhealthy(boolean markUnhealthy);

    /**
     * Set the resource status reason on the resource.
     *
     * @param resourceStatusReason
     * @return
     */
    ResourceHealthBuilder resourceStatusReason(String resourceStatusReason);
}
