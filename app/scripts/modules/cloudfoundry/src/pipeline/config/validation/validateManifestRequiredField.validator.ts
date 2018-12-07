import { get, upperFirst } from 'lodash';

import { IPipeline, IStage, IStageOrTriggerValidator, ITrigger, PipelineConfigValidator } from '@spinnaker/core';
import { IManifestFieldValidatorConfig } from 'cloudfoundry/pipeline/config/validation/ManifestConfigValidator';

export class RequiredManifestFieldValidator implements IStageOrTriggerValidator {
  public validate(
    _pipeline: IPipeline,
    stage: IStage | ITrigger,
    validationConfig: IManifestFieldValidatorConfig,
  ): string {
    const manifest: any = get(stage, 'manifest');

    if (manifest.type !== validationConfig.manifestType) {
      return null;
    }

    const content: any = get(manifest, validationConfig.fieldName);
    const fieldLabel = upperFirst(validationConfig.fieldName);
    return content ? null : `<strong>${fieldLabel}</strong> is a required field for Deploy Service stages.`;
  }
}

PipelineConfigValidator.registerValidator('requiredManifestField', new RequiredManifestFieldValidator());
