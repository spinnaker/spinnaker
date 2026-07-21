import { module } from 'angular';

import { DeploymentStrategySelector } from './DeploymentStrategySelector';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const DEPLOYMENT_STRATEGY_SELECTOR_COMPONENT = 'spinnaker.core.deploymentStrategy.deploymentStrategySelector';
module(DEPLOYMENT_STRATEGY_SELECTOR_COMPONENT, []).component(
  'deploymentStrategySelector',
  angularComponentFromReact(DeploymentStrategySelector, 'deploymentStrategySelector', [
    'command',
    'onFieldChange',
    'onStrategyChange',
    'labelColumns',
    'fieldColumns',
  ]),
);
