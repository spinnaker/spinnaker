import { module } from 'angular';

import './strategies/custom/custom.strategy';
import './strategies/highlander/highlander.strategy';
import './strategies/none/none.strategy';
import './strategies/redblack/redblack.strategy';
import './strategies/rollingredblack/rollingredblack.strategy';

import { CUSTOM_STRATEGY_SELECTOR_COMPONENT } from './strategies/custom/customStrategySelector.component';
import { PIPELINE_SELECTOR_COMPONENT } from './strategies/rollingredblack/pipelineSelector.component';
import { DEPLOYMENT_STRATEGY_SELECTOR_COMPONENT } from './deploymentStrategySelector.component';
import { DEPLOYMENT_STRATEGY_FIELDS_CONTROLLER } from './deploymentStrategyFieldsController';

export const DEPLOYMENT_STRATEGY_MODULE = 'spinnaker.core.deploymentStrategy';
module(DEPLOYMENT_STRATEGY_MODULE, [
  CUSTOM_STRATEGY_SELECTOR_COMPONENT,
  PIPELINE_SELECTOR_COMPONENT,
  DEPLOYMENT_STRATEGY_FIELDS_CONTROLLER,
  DEPLOYMENT_STRATEGY_SELECTOR_COMPONENT,
]);
