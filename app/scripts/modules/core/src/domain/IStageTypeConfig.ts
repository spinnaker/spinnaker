import { IStage } from './IStage';
import { IExecutionStageSummary } from './IExecutionStage';
import { IStageOrTriggerTypeConfig } from './IStageOrTriggerTypeConfig';

export interface IStageTypeConfig extends IStageOrTriggerTypeConfig {
  executionDetailsUrl: string;
  executionStepLabelUrl?: string;
  executionConfigSections?: string[];
  defaultTimeoutMs?: number;
  provides?: string;
  providesFor?: string[];
  cloudProvider?: string;
  cloudProviders?: string[];
  alias?: string;
  useBaseProvider?: boolean;
  executionLabelComponent?: React.Component<{ stage: IExecutionStageSummary }, any>;
  accountExtractor?: (stage: IStage) => string;
  extraLabelLines?: (stage: IStage) => number;
  restartable?: boolean;
  synthetic?: boolean;
  nameToCheckInTest?: string;
  configuration?: any;
}
