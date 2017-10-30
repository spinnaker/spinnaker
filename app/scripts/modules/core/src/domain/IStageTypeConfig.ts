import { IStage } from './IStage';
import { IExecutionDetailsSectionProps } from 'core/pipeline/config/stages/core';
import { IExecutionStageLabelComponentProps, IExecutionStageSummary } from './IExecutionStage';
import { IStageOrTriggerTypeConfig } from './IStageOrTriggerTypeConfig';

export type IExecutionDetailsSection = (React.ComponentClass<IExecutionDetailsSectionProps> | React.SFC<IExecutionDetailsSectionProps>) & { title: string };

export interface IStageTypeConfig extends IStageOrTriggerTypeConfig {
  accountExtractor?: (stage: IStage) => string;
  addAliasToConfig?: boolean;
  alias?: string;
  cloudProvider?: string;
  cloudProviders?: string[];
  configAccountExtractor?: any;
  configuration?: any;
  defaultTimeoutMs?: number;
  executionConfigSections?: string[]; // angular only
  executionDetailsSections?: IExecutionDetailsSection[]; // react only
  executionDetailsUrl?: string; // angular only
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
