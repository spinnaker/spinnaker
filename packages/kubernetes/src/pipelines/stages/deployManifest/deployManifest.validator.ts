import { get, isEmpty } from 'lodash';

import { ICustomValidator, IPipeline, IStage, IValidatorConfig } from '@spinnaker/core';

import { ManifestSource } from '../../../manifest/ManifestSource';
import { strategyRedBlack } from '../../../rolloutStrategy/redblack.strategy';

const MAX_VERSION_HISTORY_ANNOTATION = 'strategy.spinnaker.io/max-version-history';

export const deployManifestValidators = (): IValidatorConfig[] => {
  return [
    { type: 'requiredField', fieldName: 'account', fieldLabel: 'Account' },
    { type: 'requiredField', fieldName: 'source', fieldLabel: 'Source' },
    {
      type: 'custom',
      validate: (_pipeline: IPipeline, stage: IStage) => {
        const enabled = get(stage, 'trafficManagement.enabled', false);
        const services = get(stage, 'trafficManagement.options.services', []);
        if (enabled && isEmpty(services)) {
          return `Select at least one <strong>Service</strong> to enable Spinnaker-managed rollout strategy options.`;
        }
        if (enabled && stage.source === ManifestSource.TEXT) {
          const manifests = get(stage, 'manifests', []);
          const replicaSetManifests = manifests.filter((m) => m.kind === 'ReplicaSet');
          const strategy = get(stage, 'trafficManagement.options.strategy');
          const maxVersionHistory = parseInt(
            get(replicaSetManifests, [0, 'metadata', 'annotations', MAX_VERSION_HISTORY_ANNOTATION]),
            10,
          );
          if (strategy === strategyRedBlack.key && maxVersionHistory < 2) {
            return `The max version history specified in your manifest conflicts with the behavior of the Red/Black rollout strategy. Please update your <strong>${MAX_VERSION_HISTORY_ANNOTATION}</strong> annotation to a value greater than or equal to 2.`;
          }
        }
        return null;
      },
    } as ICustomValidator,
  ];
};
