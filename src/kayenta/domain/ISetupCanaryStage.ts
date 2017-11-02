import { IExecutionStage } from '@spinnaker/core';
import { ICanaryConfig } from './ICanaryConfig';

export interface ISetupCanaryStage extends IExecutionStage {
  context: {
    canaryConfigId: string;
  };
  outputs: {
    canaryConfig: ICanaryConfig;
  };
}
