import { module } from 'angular';

import { IPipeline, IStage, IStageOrTriggerTypeConfig, ITrigger } from 'core/domain';
import { BaseRequiredFieldValidator, IRequiredField } from './baseRequiredField.validator';
import { PIPELINE_CONFIG_VALIDATOR, PipelineConfigValidator } from './pipelineConfig.validator';
import { IBaseRequiredFieldValidationConfig } from 'core/pipeline/config/validation/baseRequiredField.validator';

export interface IMultiRequiredField extends IBaseRequiredFieldValidationConfig {
  fields: IRequiredField[];
}

export type IAnyFieldRequiredValidationConfig = IBaseRequiredFieldValidationConfig & IMultiRequiredField;

export class AnyFieldRequiredValidator extends BaseRequiredFieldValidator {
  protected passesValidation(
    pipeline: IPipeline,
    stage: IStage | ITrigger,
    validationConfig: IAnyFieldRequiredValidationConfig,
  ): boolean {
    return validationConfig.fields.some((requiredField: IRequiredField) => {
      return this.fieldIsValid(pipeline, stage, requiredField.fieldName);
    });
  }

  protected validationMessage(
    validationConfig: IAnyFieldRequiredValidationConfig,
    config: IStageOrTriggerTypeConfig,
  ): string {
    const fieldString: string = validationConfig.fields
      .map((requiredField: IRequiredField) => this.printableFieldLabel(requiredField))
      .join(', ');
    return (
      validationConfig.message ||
      `At least one of the following fields must be supplied for ${
        config.label
      } stages: <strong>${fieldString}</strong>.`
    );
  }
}

export const ANY_FIELD_REQUIRED_VALIDATOR = 'spinnaker.core.pipeline.config.validation.anyFieldRequired';
module(ANY_FIELD_REQUIRED_VALIDATOR, [PIPELINE_CONFIG_VALIDATOR])
  .service('anyFieldRequiredValidator', AnyFieldRequiredValidator)
  .run((pipelineConfigValidator: PipelineConfigValidator, anyFieldRequiredValidator: AnyFieldRequiredValidator) => {
    pipelineConfigValidator.registerValidator('anyFieldRequired', anyFieldRequiredValidator);
  });
