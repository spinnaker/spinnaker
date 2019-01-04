import { get, upperFirst } from 'lodash';

import { IPipeline, IStage, IStageOrTriggerValidator, ITrigger, PipelineConfigValidator } from '@spinnaker/core';
import { IServiceFieldValidatorConfig } from 'cloudfoundry/pipeline/config/validation/ServiceFieldValidatorConfig';

export class ServiceParameterJsonFieldValidator implements IStageOrTriggerValidator {
  private static validationMessage(validationConfig: IServiceFieldValidatorConfig): string {
    const fieldLabel: string = ServiceParameterJsonFieldValidator.printableFieldLabel(validationConfig);
    return validationConfig.message || `<strong>${fieldLabel}</strong> should be a valid JSON string.`;
  }

  private static printableFieldLabel(config: IServiceFieldValidatorConfig): string {
    const fieldLabel: string = config.fieldLabel || config.fieldName;
    return upperFirst(fieldLabel);
  }

  private static fieldIsValid(stage: IStage | ITrigger, config: IServiceFieldValidatorConfig): boolean {
    const serviceInput = get(stage, 'manifest');
    const content: any = get(serviceInput, config.fieldName);

    if (!content) {
      return true;
    }

    try {
      JSON.parse(content);
      return true;
    } catch (e) {
      return false;
    }
  }

  public validate(
    _pipeline: IPipeline,
    stage: IStage | ITrigger,
    validationConfig: IServiceFieldValidatorConfig,
  ): string {
    const serviceInput: any = get(stage, 'manifest');
    if (serviceInput.type !== validationConfig.serviceInputType) {
      return null;
    }

    if (!ServiceParameterJsonFieldValidator.fieldIsValid(stage, validationConfig)) {
      return ServiceParameterJsonFieldValidator.validationMessage(validationConfig);
    }
    return null;
  }
}

PipelineConfigValidator.registerValidator('validServiceParameterJson', new ServiceParameterJsonFieldValidator());
