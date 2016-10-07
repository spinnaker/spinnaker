/*
 * Copyright 2016 Target, Inc.
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
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openstack4j.openstack.heat.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import org.openstack4j.model.heat.ResourceHealth;
import org.openstack4j.model.heat.StackCreate;
import org.openstack4j.model.heat.builder.ResourceHealthBuilder;

/**
 * TODO remove once openstack4j 3.0.3 is released
 * This is a model of a HeatResourceHealth. It uses Jackson annotations for
 * (de)serialization into JSON format
 *
 * @author Dan Maas
 *
 */
public class HeatResourceHealth implements ResourceHealth {

    private static final long serialVersionUID = 1L;

    @JsonProperty("mark_unhealthy")
    private boolean markUnhealthy;

    @JsonProperty("resource_status_reason")
    private String resourceStatusReason;

    /**
     * Returnes a {@link HeatResourceHealth.HeatResourceHealthBuilder} for configuration and
     * creation of a {@link HeatResourceHealth} object.
     *
     * @return a {@link HeatResourceHealth.HeatResourceHealthBuilder}
     */
    public static HeatResourceHealth.HeatResourceHealthBuilder builder() {
        return new HeatResourceHealth.HeatResourceHealthBuilder();
    }

    @Override
    public ResourceHealthBuilder toBuilder() {
        return new HeatResourceHealth.HeatResourceHealthBuilder(this);
    }

    @Override
    public boolean isMarkUnhealthy() {
        return markUnhealthy;
    }

    @Override
    public void setMarkUnhealthy(boolean markUnhealthy) {
        this.markUnhealthy = markUnhealthy;
    }

    @Override
    public String getResourceStatusReason() {
        return resourceStatusReason;
    }

    @Override
    public void setResourceStatusReason(String resourceStatusReason) {
        this.resourceStatusReason = resourceStatusReason;
    }

    @Override
    public String toString(){
        return Objects.toStringHelper(this)
                .add("markUnhealthy", markUnhealthy)
                .add("resourceStatusReason", resourceStatusReason)
                .toString();
    }

    /**
     * A Builder to create a {@link HeatResourceHealth}. Use {@link #build()} to receive the
     * {@link StackCreate} object.
     *
     * @author Matthias Reisser
     *
     */
    public static class HeatResourceHealthBuilder implements ResourceHealthBuilder {

        private HeatResourceHealth model;

        /**
         * Constructor to create a {@link HeatResourceHealth.HeatResourceHealthBuilder} object
         * with a new, empty {@link HeatResourceHealth} object.
         */
        public HeatResourceHealthBuilder() {
            this(new HeatResourceHealth());
        }

        /**
         * Constructor for manipulation of an existing {@link HeatResourceHealth}
         * object.
         *
         * @param model the {@link HeatResourceHealth} object which is to be
         *              modified.
         */
        public HeatResourceHealthBuilder(HeatResourceHealth model) {
            this.model = model;
        }

        @Override
        public ResourceHealth build() {
            return model;
        }

        @Override
        public ResourceHealthBuilder from(ResourceHealth in) {
            model = (HeatResourceHealth)in;
            return this;
        }

        @Override
        public ResourceHealthBuilder markUnhealthy(boolean markUnhealthy) {
            model.setMarkUnhealthy(markUnhealthy);
            return this;
        }

        @Override
        public ResourceHealthBuilder resourceStatusReason(String resourceStatusReason) {
            model.setResourceStatusReason(resourceStatusReason);
            return this;
        }
    }
}
