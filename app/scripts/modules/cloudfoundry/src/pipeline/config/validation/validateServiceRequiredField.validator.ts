import { get, upperFirst } from 'lodash';

import { IPipeline, IStage, IStageOrTriggerValidator, ITrigger, PipelineConfigValidator } from '@spinnaker/core';
import { IServiceFieldValidatorConfig } from 'cloudfoundry/pipeline/config/validation/ServiceFieldValidatorConfig';

export class RequiredServiceFieldValidator implements IStageOrTriggerValidator {
  public validate(
    _pipeline: IPipeline,
    stage: IStage | ITrigger,
    validationConfig: IServiceFieldValidatorConfig,
  ): string {
    const serviceInput: any = get(stage, 'manifest');
    if (serviceInput.type !== validationConfig.serviceInputType) {
      return null;
    }
    const content: any = get(serviceInput, validationConfig.fieldName);
    const fieldLabel = upperFirst(validationConfig.fieldName);
    return content ? null : `<strong>${fieldLabel}</strong> is a required field for Deploy Service stages.`;
  }
}

PipelineConfigValidator.registerValidator('requiredServiceField', new RequiredServiceFieldValidator());
