import { IValidatorConfig } from '@spinnaker/core';

export interface IServiceFieldValidatorConfig extends IValidatorConfig {
  serviceInputType: string;
}
