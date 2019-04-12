import { get, isEmpty } from 'lodash';

import { IPipeline, IStage, IValidatorConfig, ICustomValidator } from '@spinnaker/core';

export const deployManifestValidators = (): IValidatorConfig[] => {
  return [
    {
      type: 'custom',
      validate: (_pipeline: IPipeline, stage: IStage) => {
        const enabled = get(stage, 'trafficManagement.enabled', false);
        const services = get(stage, 'trafficManagement.options.services', []);
        if (enabled && isEmpty(services)) {
          return `Select at least one <strong>Service</strong> to enable Spinnaker-managed rollout strategy options.`;
        }
        return null;
      },
    } as ICustomValidator,
  ];
};
