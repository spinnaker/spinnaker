import { DeploymentStrategyRegistry, IServerGroupCommand } from '@spinnaker/core';

import { AdditionalFields } from './AdditionalFields';
import { AWSProviderSettings } from '../aws.settings';

export interface IRollingPushTermination {
  concurrentRelaunches: number;
  relaunchAllInstances: boolean;
  totalRelaunches: number;

  [key: string]: any;
}

export interface IRollingPushCommand extends IServerGroupCommand {
  termination: IRollingPushTermination;
}

DeploymentStrategyRegistry.registerStrategy({
  label: 'Rolling Push (not recommended)',
  description: `Updates the launch ${
    AWSProviderSettings.serverGroups?.enableLaunchTemplates ? 'template' : 'configuration'
  } for this server group, then terminates instances incrementally,
    replacing them with instances launched with the updated configuration. This is not a best practice - it goes against
    the principles of immutable infrastructure - but may be necessary in some cases.`,
  key: 'rollingpush',
  providerRestricted: true,
  additionalFields: [
    'termination.totalRelaunches',
    'termination.concurrentRelaunches',
    'termination.order',
    'termination.relaunchAllInstances',
  ],
  AdditionalFieldsComponent: AdditionalFields,
  initializationMethod: (command: IRollingPushCommand) => {
    command.termination = command.termination || {
      order: 'oldest',
      relaunchAllInstances: true,
      concurrentRelaunches: 1,
      totalRelaunches: Number(command?.capacity?.max) ?? 1,
    };
  },
});
