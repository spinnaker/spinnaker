/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup;

import com.netflix.spinnaker.orca.DefaultTaskResult;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
public class EnsureInterestingHealthProviderNamesTask implements Task, CloudProviderAware {
  private Collection<InterestingHealthProviderNamesSupplier> interestingHealthProviderNamesSuppliers;

  @Autowired
  public EnsureInterestingHealthProviderNamesTask(Collection<InterestingHealthProviderNamesSupplier> interestingHealthProviderNamesSuppliers) {
    this.interestingHealthProviderNamesSuppliers = interestingHealthProviderNamesSuppliers;
  }

  @Override
  public TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage);
    Optional<InterestingHealthProviderNamesSupplier> healthProviderNamesSupplierOptional = interestingHealthProviderNamesSuppliers
      .stream()
      .filter(supplier -> supplier.supports(cloudProvider, stage))
      .findFirst();

    if (healthProviderNamesSupplierOptional.isPresent()) {
      List<String> interestingHealthProviderNames = healthProviderNamesSupplierOptional.get().process(cloudProvider, stage);
      Map<String, List<String>> results = new HashMap<>();

      if (interestingHealthProviderNames != null) {
        // avoid a `null` value that may cause problems with ImmutableMap usage downstream
        results.put("interestingHealthProviderNames", interestingHealthProviderNames);
      }

      return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, results);
    }

    return new DefaultTaskResult(ExecutionStatus.SUCCEEDED);

  }
}
