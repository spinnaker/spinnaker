import { module } from 'angular';

export const DEPLOYMENT_STRATEGY_MODULE = 'spinnaker.core.deploymentStrategy';
module(DEPLOYMENT_STRATEGY_MODULE, [
  require('./deploymentStrategySelector.directive'),
  require('./deploymentStrategyConfig.provider'),
  require('./deploymentStrategySelector.controller'),
  require('./services/deploymentStrategy.service')
]);
