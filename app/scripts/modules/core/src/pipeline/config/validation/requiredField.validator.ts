import { module } from 'angular';

import { IPipeline, IStage, IStageOrTriggerTypeConfig, ITrigger } from 'core/domain';
import { BaseRequiredFieldValidator, IRequiredField } from './baseRequiredField.validator';
import { PIPELINE_CONFIG_VALIDATOR, PipelineConfigValidator } from './pipelineConfig.validator';
import { IBaseRequiredFieldValidationConfig } from 'core/pipeline/config/validation/baseRequiredField.validator';

export interface IRequiredField {
  fieldName: string;
  fieldLabel?: string;
}

export type IRequiredFieldValidationConfig = IBaseRequiredFieldValidationConfig & IRequiredField;

export class RequiredFieldValidator extends BaseRequiredFieldValidator {
  protected passesValidation(
    pipeline: IPipeline,
    stage: IStage | ITrigger,
    validationConfig: IRequiredFieldValidationConfig,
  ): boolean {
    return this.fieldIsValid(pipeline, stage, validationConfig.fieldName);
  }

  protected validationMessage(
    validationConfig: IRequiredFieldValidationConfig,
    config: IStageOrTriggerTypeConfig,
  ): string {
    const fieldLabel: string = this.printableFieldLabel(validationConfig);
    return validationConfig.message || `<strong>${fieldLabel}</strong> is a required field for ${config.label} stages.`;
  }
}

export const REQUIRED_FIELD_VALIDATOR = 'spinnaker.core.pipeline.config.validation.requiredField';
module(REQUIRED_FIELD_VALIDATOR, [PIPELINE_CONFIG_VALIDATOR])
  .service('requiredFieldValidator', RequiredFieldValidator)
  .run((pipelineConfigValidator: PipelineConfigValidator, requiredFieldValidator: RequiredFieldValidator) => {
    pipelineConfigValidator.registerValidator('requiredField', requiredFieldValidator);
  });
