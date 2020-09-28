/*
 * Copyright 2020 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.appengine.deploy.converters;

import com.netflix.spinnaker.clouddriver.appengine.AppengineOperation;
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.DeployAppengineConfigDescription;
import com.netflix.spinnaker.clouddriver.appengine.deploy.ops.DeployAppengineConfigAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import groovy.util.logging.Slf4j;
import java.util.Map;
import org.springframework.stereotype.Component;

@AppengineOperation(AtomicOperations.DEPLOY_APPENGINE_CONFIG)
@Component
@Slf4j
public class DeployAppengineConfigAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  public AtomicOperation convertOperation(Map<String, Object> input) {
    return new DeployAppengineConfigAtomicOperation(convertDescription(input));
  }

  @Override
  public DeployAppengineConfigDescription convertDescription(Map<String, Object> input) {
    DeployAppengineConfigDescription description =
        (DeployAppengineConfigDescription)
            AppengineAtomicOperationConverterHelper.convertDescription(
                input, this, DeployAppengineConfigDescription.class);
    return description;
  }
}
