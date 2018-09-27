import { get, has, upperFirst } from 'lodash';

import {
  IPipeline,
  IStage,
  IStageOrTriggerValidator,
  ITrigger,
  IValidatorConfig,
  PipelineConfigValidator,
} from '@spinnaker/core';

export interface IServiceParameterJsonValidationConfig extends IValidatorConfig {
  fieldName: string;
  fieldLabel?: string;
  message?: string;
}

export class ServiceParameterJsonFieldValidator implements IStageOrTriggerValidator {
  public validate(
    _pipeline: IPipeline,
    stage: IStage | ITrigger,
    validationConfig: IServiceParameterJsonValidationConfig,
  ): string {
    if (!this.fieldIsValid(stage, validationConfig)) {
      return this.validationMessage(validationConfig);
    }
    return null;
  }

  private validationMessage(validationConfig: IServiceParameterJsonValidationConfig): string {
    const fieldLabel: string = this.printableFieldLabel(validationConfig);
    return validationConfig.message || `<strong>${fieldLabel}</strong> should be a valid JSON string.`;
  }

  private printableFieldLabel(config: IServiceParameterJsonValidationConfig): string {
    const fieldLabel: string = config.fieldLabel || config.fieldName;
    return upperFirst(fieldLabel);
  }

  private fieldIsValid(stage: IStage | ITrigger, config: IServiceParameterJsonValidationConfig): boolean {
    const fieldExists = has(stage, config.fieldName);
    const field: any = get(stage, config.fieldName);

    if (!fieldExists || !field) {
      return true;
    }

    try {
      JSON.parse(field);
      return true;
    } catch (e) {
      return false;
    }
  }
}

PipelineConfigValidator.registerValidator('validServiceParameterJson', new ServiceParameterJsonFieldValidator());
