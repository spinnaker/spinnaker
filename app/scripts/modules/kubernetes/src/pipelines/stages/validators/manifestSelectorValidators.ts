import { ICustomValidator, IPipeline, IStage, IValidatorConfig } from '@spinnaker/core';

import { IManifestSelector, SelectorMode } from '../../../manifest/selector/IManifestSelector';

export const manifestSelectorValidators = (stageName: string): IValidatorConfig[] => {
  const required = (field: string) => `<strong>${field}</strong> is a required field for ${stageName} stages.`;

  return [
    { type: 'requiredField', fieldName: 'location', fieldLabel: 'Namespace' },
    { type: 'requiredField', fieldName: 'account', fieldLabel: 'Account' },
    {
      type: 'custom',
      validate: (_pipeline: IPipeline, stage: IManifestSelector & IStage) => {
        if (stage.mode === SelectorMode.Dynamic) {
          if (!stage.kind) {
            return required('Kind');
          }
          if (!stage.cluster) {
            return required('Cluster');
          }
          if (!stage.criteria) {
            return required('Target');
          }
        } else if (stage.mode === SelectorMode.Static) {
          const [kind] = (stage.manifestName || '').split(' ');
          if (!kind) {
            return required('Kind');
          }
          const [, name] = (stage.manifestName || '').split(' ');
          if (!name) {
            return required('Name');
          }
        } else if (stage.mode === SelectorMode.Label) {
          if (!stage.kinds) {
            return required('Kinds');
          }
        }
        return null;
      },
    } as ICustomValidator,
  ];
};
