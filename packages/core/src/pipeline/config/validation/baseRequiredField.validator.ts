import { get, has, upperFirst } from 'lodash';

import { IStageOrTriggerValidator, IValidatorConfig } from './PipelineConfigValidator';
import { IPipeline, IStage, IStageOrTriggerTypeConfig, ITrigger } from '../../../domain';

export interface IRequiredField {
  fieldName: string;
  fieldLabel?: string;
}

export interface IBaseRequiredFieldValidationConfig extends IValidatorConfig {
  message?: string;
}

export abstract class BaseRequiredFieldValidator implements IStageOrTriggerValidator {
  public validate(
    pipeline: IPipeline,
    stage: IStage | ITrigger,
    validationConfig: IBaseRequiredFieldValidationConfig,
    config: IStageOrTriggerTypeConfig,
  ): string {
    if (!this.passesValidation(pipeline, stage, validationConfig)) {
      return this.validationMessage(validationConfig, config);
    }
    return null;
  }

  protected abstract passesValidation(
    pipeline: IPipeline,
    stage: IStage | ITrigger,
    validationConfig: IBaseRequiredFieldValidationConfig,
  ): boolean;

  protected abstract validationMessage(
    validationConfig: IBaseRequiredFieldValidationConfig,
    config: IStageOrTriggerTypeConfig,
  ): string;

  protected printableFieldLabel(field: IRequiredField): string {
    const fieldLabel: string = field.fieldLabel || field.fieldName;
    return upperFirst(fieldLabel);
  }

  protected fieldIsValid(pipeline: IPipeline, stage: IStage | ITrigger, fieldName: string): boolean {
    if (pipeline.strategy === true && ['cluster', 'regions', 'zones', 'credentials'].includes(fieldName)) {
      return true;
    }

    const fieldExists = has(stage, fieldName);
    const field: any = get(stage, fieldName);

    return fieldExists && (field || field === 0) && !(Array.isArray(field) && field.length === 0);
  }
}
