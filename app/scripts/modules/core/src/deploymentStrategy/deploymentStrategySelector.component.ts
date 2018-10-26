import { module } from 'angular';
import { react2angular } from 'react2angular';

import { DeploymentStrategySelector } from './DeploymentStrategySelector';

export const DEPLOYMENT_STRATEGY_SELECTOR_COMPONENT = 'spinnaker.core.deploymentStrategy.deploymentStrategySelector';
module(DEPLOYMENT_STRATEGY_SELECTOR_COMPONENT, []).component(
  'deploymentStrategySelector',
  react2angular(DeploymentStrategySelector, ['command', 'onStrategyChange', 'labelColumns', 'fieldColumns']),
);
