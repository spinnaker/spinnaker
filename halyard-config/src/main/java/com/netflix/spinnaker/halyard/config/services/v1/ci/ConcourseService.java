/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.services.v1.ci;

import com.netflix.spinnaker.halyard.config.model.v1.ci.concourse.ConcourseCi;
import com.netflix.spinnaker.halyard.config.model.v1.ci.concourse.ConcourseMaster;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.services.v1.LookupService;
import com.netflix.spinnaker.halyard.config.services.v1.ValidateService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConcourseService extends CiService<ConcourseMaster, ConcourseCi> {
    public ConcourseService(LookupService lookupService, ValidateService validateService) {
        super(lookupService, validateService);
    }

    public String ciName() {
        return "concourse";
    }

    protected List<ConcourseCi> getMatchingCiNodes(NodeFilter filter) {
        return lookupService.getMatchingNodesOfType(filter, ConcourseCi.class);
    }

    protected List<ConcourseMaster> getMatchingAccountNodes(NodeFilter filter) {
        return lookupService.getMatchingNodesOfType(filter, ConcourseMaster.class);
    }
}
