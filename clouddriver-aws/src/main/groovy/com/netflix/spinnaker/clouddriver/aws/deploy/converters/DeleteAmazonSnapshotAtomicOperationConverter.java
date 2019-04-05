/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.converters;

import com.netflix.spinnaker.clouddriver.aws.AmazonOperation;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteAmazonSnapshotDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.DeleteAmazonSnapshotAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.netflix.spectator.api.Registry;
import java.util.Map;

@AmazonOperation(AtomicOperations.DELETE_SNAPSHOT)
@Component
public class DeleteAmazonSnapshotAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  private final Registry registry;

  @Autowired
  public DeleteAmazonSnapshotAtomicOperationConverter(Registry registry) {
    this.registry = registry;
  }

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new DeleteAmazonSnapshotAtomicOperation(convertDescription(input), registry);
  }

  @Override
  public DeleteAmazonSnapshotDescription convertDescription(Map input) {
    DeleteAmazonSnapshotDescription converted = getObjectMapper()
      .convertValue(input, DeleteAmazonSnapshotDescription.class);
    converted.setCredentials(getCredentialsObject((String) input.get("credentials")));
    return converted;
  }
}
