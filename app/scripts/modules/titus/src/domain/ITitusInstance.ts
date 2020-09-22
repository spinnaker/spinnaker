import { IInstance } from '@spinnaker/core';

export interface ITitusInstance extends IInstance {
  instanceType?: string;
  jobId?: string;
}
