import { module } from 'angular';

export const DEPLOYMENT_STRATEGY_MODULE = 'spinnaker.core.deploymentStrategy';
module(DEPLOYMENT_STRATEGY_MODULE, [
  require('./deploymentStrategySelector.directive'),
  require('./deploymentStrategyConfig.provider'),
  require('./deploymentStrategySelector.controller'),
  require('./services/deploymentStrategy.service'),
  require('./strategies/custom/custom.strategy.module'),
  require('./strategies/highlander/highlander.strategy.module'),
  require('./strategies/none/none.strategy.module'),
  require('./strategies/redblack/redblack.strategy.module'),
  require('./strategies/rollingPush/rollingPush.strategy.module'),
]);
