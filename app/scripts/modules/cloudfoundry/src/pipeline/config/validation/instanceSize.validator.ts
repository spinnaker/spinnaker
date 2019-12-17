import { get, has, upperFirst } from 'lodash';

import {
  IPipeline,
  IStage,
  IStageOrTriggerValidator,
  ITrigger,
  IValidatorConfig,
  PipelineConfigValidator,
} from '@spinnaker/core';

export interface IInstanceFieldSizeValidationConfig extends IValidatorConfig {
  fieldName: string;
  fieldLabel?: string;
  min?: number;
  max?: number;
  message?: string;
}

export class CfInstanceSizeFieldValidator implements IStageOrTriggerValidator {
  public validate(
    pipeline: IPipeline,
    stage: IStage | ITrigger,
    validationConfig: IInstanceFieldSizeValidationConfig,
  ): string {
    if (!this.passesValidation(stage, validationConfig)) {
      return this.validationMessage(validationConfig, pipeline);
    }
    return null;
  }

  protected passesValidation(stage: IStage | ITrigger, validationConfig: IInstanceFieldSizeValidationConfig): boolean {
    return this.fieldIsValid(stage, validationConfig);
  }

  protected validationMessage(validationConfig: IInstanceFieldSizeValidationConfig, pipeline: IPipeline): string {
    const fieldLabel: string = this.printableFieldLabel(validationConfig);
    const hasMin = has(validationConfig, 'min');
    const min: any = hasMin ? get(validationConfig, 'min') : 'NA';
    const hasMax = has(validationConfig, 'max');
    const max: any = hasMax ? get(validationConfig, 'max') : 'NA';
    let message = ``;
    if (hasMin) {
      if (hasMax) {
        message = `<strong>${fieldLabel}</strong> should be from ${min} to ${max} in ${pipeline.name}.`;
      } else {
        message = `<strong>${fieldLabel}</strong> should be at least ${min} in ${pipeline.name}.`;
      }
    } else if (hasMax) {
      message = `<strong>${fieldLabel}</strong> should be no more than ${max} in ${pipeline.name}.`;
    }
    return validationConfig.message || message;
  }

  protected printableFieldLabel(config: IInstanceFieldSizeValidationConfig): string {
    const fieldLabel: string = config.fieldLabel || config.fieldName;
    return upperFirst(fieldLabel);
  }

  protected fieldIsValid(stage: IStage | ITrigger, config: IInstanceFieldSizeValidationConfig): boolean {
    const fieldExists = has(stage, config.fieldName);
    const field: any = get(stage, config.fieldName);
    const hasMax: boolean = has(config, 'max');
    const hasMin: boolean = has(config, 'min');
    const max: number = get(config, 'max');
    const min: number = get(config, 'min');

    return fieldExists && (!hasMax || (hasMax && field <= max)) && (!hasMin || (hasMin && field >= min));
  }
}

PipelineConfigValidator.registerValidator('cfInstanceSizeField', new CfInstanceSizeFieldValidator());
