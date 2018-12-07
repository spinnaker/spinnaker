import { IValidatorConfig } from '@spinnaker/core';

export interface IManifestFieldValidatorConfig extends IValidatorConfig {
  manifestType: string;
}
