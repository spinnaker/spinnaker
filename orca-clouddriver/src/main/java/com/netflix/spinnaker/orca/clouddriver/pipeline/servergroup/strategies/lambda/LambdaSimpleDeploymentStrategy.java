/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.lambda;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaSimpleStrategyInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaCloudOperationOutput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaDeploymentStrategyOutput;
import com.netflix.spinnaker.orca.clouddriver.utils.LambdaCloudDriverUtils;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaSimpleDeploymentStrategy
    extends BaseLambdaDeploymentStrategy<LambdaSimpleStrategyInput> {

  @Autowired private LambdaCloudDriverUtils utils;

  @Autowired CloudDriverConfigurationProperties props;

  @Override
  public LambdaDeploymentStrategyOutput deploy(LambdaSimpleStrategyInput inp) {
    String cloudDriverUrl = props.getCloudDriverBaseUrl();
    Map<String, Object> outputMap = new HashMap<String, Object>();
    outputMap.put("deployment:majorVersionDeployed", inp.getMajorFunctionVersion());
    outputMap.put("deployment:strategyUsed", "SimpleDeploymentStrategy");
    outputMap.put("deployment:aliasDeployed", inp.getAliasName());
    LambdaCloudOperationOutput out = postToCloudDriver(inp, cloudDriverUrl, utils);
    out.setOutputMap(outputMap);
    LambdaDeploymentStrategyOutput deployOutput = LambdaDeploymentStrategyOutput.builder().build();
    deployOutput.setSucceeded(true);
    deployOutput.setOutput(out);
    return deployOutput;
  }

  @Override
  public LambdaSimpleStrategyInput setupInput(StageExecution stage) {
    LambdaSimpleStrategyInput aliasInp = utils.getInput(stage, LambdaSimpleStrategyInput.class);

    aliasInp.setCredentials(aliasInp.getAccount());
    aliasInp.setAppName(stage.getExecution().getApplication());

    aliasInp.setMajorFunctionVersion(
        getVersion(stage, aliasInp.getVersionNameA(), aliasInp.getVersionNumberA()));
    return aliasInp;
  }

  @Override
  public LambdaCloudDriverUtils getUtils() {
    return utils;
  }
}
