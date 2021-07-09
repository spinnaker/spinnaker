import { module } from 'angular';
import { react2angular } from 'react2angular';

import { DeploymentStrategySelector } from './DeploymentStrategySelector';
import { withErrorBoundary } from '../presentation/SpinErrorBoundary';

export const DEPLOYMENT_STRATEGY_SELECTOR_COMPONENT = 'spinnaker.core.deploymentStrategy.deploymentStrategySelector';
module(DEPLOYMENT_STRATEGY_SELECTOR_COMPONENT, []).component(
  'deploymentStrategySelector',
  react2angular(withErrorBoundary(DeploymentStrategySelector, 'deploymentStrategySelector'), [
    'command',
    'onFieldChange',
    'onStrategyChange',
    'labelColumns',
    'fieldColumns',
  ]),
);
