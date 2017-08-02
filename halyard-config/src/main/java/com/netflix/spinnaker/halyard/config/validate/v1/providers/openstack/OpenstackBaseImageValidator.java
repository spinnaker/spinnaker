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

package com.netflix.spinnaker.halyard.config.validate.v1.providers.openstack;

import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.openstack.OpenstackBaseImage;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.util.StringUtils;

import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Data
public class OpenstackBaseImageValidator extends Validator<OpenstackBaseImage> {
    final private List<OpenstackNamedAccountCredentials> credentialsList;

    final private String halyardVersion;

    public void validate(ConfigProblemSetBuilder p, OpenstackBaseImage n) {
        DaemonTaskHandler.message("Validating " + n.getNodeName() + " with " + OpenstackBaseImageValidator.class.getSimpleName());

        OpenstackBaseImage.OpenstackVirtualizationSettings vs = n.getVirtualizationSettings().get(0);
        String region = vs.getRegion();
        String instanceType = vs.getInstanceType();
        String sourceImageId = vs.getSourceImageId();
        String sshUserName = vs.getSshUserName();

        if (StringUtils.isEmpty(region)) {
            p.addProblem(Problem.Severity.ERROR, "No region supplied for openstack base image.");
        }

        if (StringUtils.isEmpty(instanceType)) {
            p.addProblem(Problem.Severity.ERROR, "No instance type supplied for openstack base image.");
        }

        if (StringUtils.isEmpty(sourceImageId)) {
            p.addProblem(Problem.Severity.ERROR, "No source image id supplied for openstack base image.");
        }

        if (StringUtils.isEmpty(sshUserName)) {
            p.addProblem(Problem.Severity.ERROR, "No ssh username supplied for openstack base image.");
        }
        // TODO(shazy792) Add check to see if image actually exists on openstack instance
    }
}
