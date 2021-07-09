import { defaultsDeep } from 'lodash';

import { AdditionalFields } from './AdditionalFields';
import { DeploymentStrategyRegistry } from '../../deploymentStrategy.registry';
import { IServerGroupCommand } from '../../../serverGroup';

export interface IRedBlackCommand extends IServerGroupCommand {
  maxRemainingAsgs: number;
  delayBeforeDisableSec: number;
  delayBeforeScaleDownSec: number;
  rollback: {
    onFailure: boolean;
  };
  scaleDown: boolean;
}

DeploymentStrategyRegistry.registerStrategy({
  label: 'Red/Black',
  description:
    'Disables <i>all</i> previous server groups in the cluster as soon as new server group passes health checks',
  key: 'redblack',
  providerRestricted: true,
  additionalFields: ['scaleDown', 'maxRemainingAsgs'],
  AdditionalFieldsComponent: AdditionalFields,
  initializationMethod: (command: IRedBlackCommand) => {
    defaultsDeep(command, {
      rollback: {
        onFailure: false,
      },
      maxRemainingAsgs: command.maxRemainingAsgs ?? 2,
      delayBeforeDisableSec: 0,
      delayBeforeScaleDownSec: 0,
      scaleDown: false,
    });
  },
});
