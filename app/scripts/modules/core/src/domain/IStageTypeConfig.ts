import { IStage } from './IStage';
import { IExecutionStageLabelComponentProps, IExecutionStageSummary } from './IExecutionStage';
import { IStageOrTriggerTypeConfig } from './IStageOrTriggerTypeConfig';

export interface IStageTypeConfig extends IStageOrTriggerTypeConfig {
  accountExtractor?: (stage: IStage) => string;
  addAliasToConfig?: boolean;
  alias?: string;
  cloudProvider?: string;
  cloudProviders?: string[];
  configAccountExtractor?: any;
  configuration?: any;
  defaultTimeoutMs?: number;
  executionConfigSections?: string[];
  executionDetailsUrl?: string;
  executionLabelComponent?: React.ComponentClass<IExecutionStageLabelComponentProps>;
  executionStepLabelUrl?: string;
  executionSummaryUrl?: string;
  extraLabelLines?: (stage: IStage) => number;
  markerIcon?: React.ComponentClass<{ stage: IExecutionStageSummary }>;
  nameToCheckInTest?: string;
  provides?: string;
  providesFor?: string[];
  restartable?: boolean;
  stageFilter?: (stage: IStage) => boolean;
  strategy?: boolean;
  synthetic?: boolean;
  useBaseProvider?: boolean;
  useCustomTooltip?: boolean;
}
