import { IInstance } from '@spinnaker/core';

export interface ICloudFoundryInstance extends IInstance {
  key: string;
  details?: string;
}
