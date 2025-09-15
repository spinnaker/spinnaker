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

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaTrafficUpdateStrategyInjector {
  private static final Logger logger =
      LoggerFactory.getLogger(LambdaTrafficUpdateStrategyInjector.class);

  @Autowired private LambdaSimpleDeploymentStrategy simpleStrat;
  @Autowired private LambdaWeightedDeploymentStrategy weightedStrat;
  @Autowired private LambdaBlueGreenDeploymentStrategy blueGreenStrat;

  private final Map<LambdaDeploymentStrategyEnum, BaseLambdaDeploymentStrategy> factoryMap =
      new HashMap<>();

  public LambdaTrafficUpdateStrategyInjector() {
    logger.debug("Start strategy injector");
  }

  public BaseLambdaDeploymentStrategy getStrategy(LambdaDeploymentStrategyEnum inp) {
    return factoryMap.get(inp);
  }

  @PostConstruct
  private void injectEnum() {
    factoryMap.put(LambdaDeploymentStrategyEnum.$BLUEGREEN, blueGreenStrat);
    factoryMap.put(LambdaDeploymentStrategyEnum.$WEIGHTED, weightedStrat);
    factoryMap.put(LambdaDeploymentStrategyEnum.$SIMPLE, simpleStrat);
  }
}
