import type { IExecutionStage } from '@spinnaker/core';
import type { ICanaryConfig } from './ICanaryConfig';

export interface ISetupCanaryStage extends IExecutionStage {
  context: {
    canaryConfigId: string;
  };
  outputs: {
    canaryConfig: ICanaryConfig;
  };
}
