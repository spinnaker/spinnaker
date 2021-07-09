import { module } from 'angular';

import { DEPLOYMENT_STRATEGY_SELECTOR_COMPONENT } from './deploymentStrategySelector.component';
import './strategies/custom/custom.strategy';
import './strategies/highlander/highlander.strategy';
import './strategies/monitored/monitored.strategy';
import './strategies/none/none.strategy';
import './strategies/redblack/redblack.strategy';
import './strategies/rollingredblack/rollingredblack.strategy';

export const DEPLOYMENT_STRATEGY_MODULE = 'spinnaker.core.deploymentStrategy';
module(DEPLOYMENT_STRATEGY_MODULE, [DEPLOYMENT_STRATEGY_SELECTOR_COMPONENT]);
