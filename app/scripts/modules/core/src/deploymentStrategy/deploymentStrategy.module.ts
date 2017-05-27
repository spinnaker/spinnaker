import { module } from 'angular';

import './strategies/custom/custom.strategy';
import './strategies/highlander/highlander.strategy';
import './strategies/none/none.strategy';
import './strategies/redblack/redblack.strategy';

import { CUSTOM_STRATEGY_SELECTOR_COMPONENT } from './strategies/custom/customStrategySelector.component';
import { DEPLOYMENT_STRATEGY_SELECTOR_COMPONENT } from './deploymentStrategySelector.component';

export const DEPLOYMENT_STRATEGY_MODULE = 'spinnaker.core.deploymentStrategy';
module(DEPLOYMENT_STRATEGY_MODULE, [
  CUSTOM_STRATEGY_SELECTOR_COMPONENT,
  DEPLOYMENT_STRATEGY_SELECTOR_COMPONENT,
]);
