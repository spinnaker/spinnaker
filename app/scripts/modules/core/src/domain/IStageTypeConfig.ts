import { IStage } from './IStage';
import { IExecutionDetailsSectionProps } from 'core/pipeline/config/stages/common';
import { IExecutionContext, IExecutionStageLabelProps, IExecutionStageSummary } from './IExecutionStage';
import { IStageOrTriggerTypeConfig } from './IStageOrTriggerTypeConfig';

export type IExecutionDetailsSection = React.ComponentType<IExecutionDetailsSectionProps> & { title: string };

export interface IStageTypeConfig extends IStageOrTriggerTypeConfig {
  accountExtractor?: (stage: IStage) => string[];
  addAliasToConfig?: boolean;
  alias?: string;
  artifactExtractor?: (stageContext: IExecutionContext) => string[];
  artifactRemover?: (stage: IStage, artifactId: string) => void;
  cloudProvider?: string;
  cloudProviders?: string[];
  configAccountExtractor?: (stage: IStage) => string[];
  configuration?: any;
  defaults?: any;
  defaultTimeoutMs?: number;
  disableNotifications?: boolean;
  executionConfigSections?: string[]; // angular only
  executionDetailsSections?: IExecutionDetailsSection[]; // react only
  executionDetailsUrl?: string; // angular only
  executionLabelComponent?: React.ComponentType<IExecutionStageLabelProps>;
  executionStepLabelUrl?: string;
  executionSummaryUrl?: string;
  extraLabelLines?: (stage: IStage) => number;
  markerIcon?: React.ComponentClass<{ stage: IExecutionStageSummary }>;
  nameToCheckInTest?: string;
  provides?: string;
  providesFor?: string[];
  producesArtifacts?: boolean;
  restartable?: boolean;
  stageFilter?: (stage: IStage) => boolean;
  strategy?: boolean;
  synthetic?: boolean;
  useBaseProvider?: boolean;
  useCustomTooltip?: boolean;
}
