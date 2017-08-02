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

package com.netflix.spinnaker.halyard.config.model.v1.providers.openstack;

import com.netflix.spinnaker.halyard.config.model.v1.node.BaseImage;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;


@Data
@ToString
@EqualsAndHashCode(callSuper = true)
public class OpenstackBaseImage extends BaseImage<OpenstackBaseImage.OpenstackImageSettings, List<OpenstackBaseImage.OpenstackVirtualizationSettings>> {
    @Override
    public void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
        v.validate(psBuilder, this);
    }

    private OpenstackImageSettings baseImage;
    private List<OpenstackVirtualizationSettings> virtualizationSettings;

    @EqualsAndHashCode(callSuper = true)
    @Data
    @ToString(callSuper = true)
    public static class OpenstackImageSettings extends BaseImage.ImageSettings {
        // We have none to set
    }

    @Data
    @ToString
    public static class OpenstackVirtualizationSettings {
        String region;
        String instanceType;
        String sourceImageId;
        String sshUserName;
    }
}
