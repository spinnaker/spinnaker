import {module} from 'angular';
import {get, has} from 'lodash';

import {
  PIPELINE_CONFIG_VALIDATOR, IStageOrTriggerTypeConfig, IStageOrTriggerValidator, IValidatorConfig,
  PipelineConfigValidator
} from './pipelineConfig.validator';
import {IStage} from 'core/domain/IStage';
import {ITrigger} from 'core/domain/ITrigger';
import {IPipeline} from 'core/domain/IPipeline';

export interface IRequiredFieldValidationConfig extends IValidatorConfig {
  fieldName: string;
  fieldLabel?: string;
  message?: string;
}

export class RequiredFieldValidator implements IStageOrTriggerValidator {

  public validate(pipeline: IPipeline, stage: IStage | ITrigger, validationConfig: IRequiredFieldValidationConfig, config: IStageOrTriggerTypeConfig): string {
    if (pipeline.strategy === true && ['cluster', 'regions', 'zones', 'credentials'].includes(validationConfig.fieldName)) {
      return null;
    }

    let fieldLabel: string = validationConfig.fieldLabel || validationConfig.fieldName;
    fieldLabel = fieldLabel.charAt(0).toUpperCase() + fieldLabel.substr(1);
    const validationMessage = validationConfig.message || `<strong>${fieldLabel}</strong> is a required field for ${config.label} stages.`;
    const fieldExists = has(stage, validationConfig.fieldName);
    const field: any = get(stage, validationConfig.fieldName);

    if (!fieldExists || (!field && field !== 0) || (field instanceof Array && field.length === 0)) {
      return validationMessage;
    }
    return null;
  }
}

export const REQUIRED_FIELD_VALIDATOR = 'spinnaker.core.pipeline.config.validation.requiredField';
module(REQUIRED_FIELD_VALIDATOR, [
  PIPELINE_CONFIG_VALIDATOR
])
  .service('requiredFieldValidator', RequiredFieldValidator)
  .run((pipelineConfigValidator: PipelineConfigValidator, requiredFieldValidator: RequiredFieldValidator) => {
    pipelineConfigValidator.registerValidator('requiredField', requiredFieldValidator);
  });
