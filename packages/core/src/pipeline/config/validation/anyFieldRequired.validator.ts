import { PipelineConfigValidator } from './PipelineConfigValidator';
import { BaseRequiredFieldValidator, IRequiredField } from './baseRequiredField.validator';
import { IBaseRequiredFieldValidationConfig } from './baseRequiredField.validator';
import { IPipeline, IStage, IStageOrTriggerTypeConfig, ITrigger } from '../../../domain';

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
      `At least one of the following fields must be supplied for ${config.label} stages: <strong>${fieldString}</strong>.`
    );
  }
}

PipelineConfigValidator.registerValidator('anyFieldRequired', new AnyFieldRequiredValidator());
