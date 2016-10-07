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

package org.openstack4j.model.heat;

import org.openstack4j.common.Buildable;
import org.openstack4j.model.ModelEntity;
import org.openstack4j.model.heat.builder.ResourceHealthBuilder;

/**
 * TODO remove once openstack4j 3.0.3 is released
 * This interface describes the getter-methods (and thus components) of a mark resource unhealthy request.
 *
 * @see http://developer.openstack.org/api-ref-orchestration-v1.html
 *
 * @author Dan Maas
 *
 */
public interface ResourceHealth extends ModelEntity, Buildable<ResourceHealthBuilder> {

    boolean isMarkUnhealthy();

    void setMarkUnhealthy(boolean markUnhealthy);

    String getResourceStatusReason();

    void setResourceStatusReason(String resourceStatusReason);


}
