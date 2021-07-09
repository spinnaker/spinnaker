import { IInstance } from '@spinnaker/core';

export interface ICloudFoundryInstance extends IInstance {
  details?: string;
}
