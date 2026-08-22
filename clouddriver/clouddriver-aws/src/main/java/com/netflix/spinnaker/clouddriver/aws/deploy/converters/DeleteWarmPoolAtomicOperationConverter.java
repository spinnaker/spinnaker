/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.aws.deploy.converters;

import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteWarmPoolDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.DeleteWarmPoolAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component("deleteWarmPoolDescription")
public class DeleteWarmPoolAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new DeleteWarmPoolAtomicOperation(convertDescription(input));
  }

  @Override
  public DeleteWarmPoolDescription convertDescription(Map input) {
    DeleteWarmPoolDescription converted =
        getObjectMapper().convertValue(input, DeleteWarmPoolDescription.class);
    converted.setCredentials(getCredentialsObject((String) input.get("credentials")));
    return converted;
  }
}
