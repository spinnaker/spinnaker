import { IExecutionContext, IExecutionStageLabelProps, IExecutionStageSummary } from './IExecutionStage';
import { IStage } from './IStage';
import { IStageOrTriggerTypeConfig } from './IStageOrTriggerTypeConfig';
import { IExecutionDetailsSectionProps } from '../pipeline/config/stages/common';
import { IStageSummaryProps } from '../pipeline/details/StageSummary';

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
  disableNotifications?: boolean;
  executionConfigSections?: string[]; // angular only
  executionDetailsSections?: IExecutionDetailsSection[]; // react only
  executionDetailsUrl?: string; // angular only
  executionLabelComponent?: React.ComponentType<IExecutionStageLabelProps>;
  executionStepLabelUrl?: string;
  executionSummaryUrl?: string;
  executionSummaryComponent?: React.ComponentType<IStageSummaryProps>;
  extraLabelLines?: (stage: IStage) => number;
  markerIcon?: React.ComponentClass<{ stage: IExecutionStageSummary }>;
  nameToCheckInTest?: string;
  provides?: string;
  providesFor?: string[];
  producesArtifacts?: boolean;
  restartable?: boolean;
  stageFilter?: (stage: IStage) => boolean;
  strategy?: boolean;
  supportsCustomTimeout?: boolean;
  synthetic?: boolean;
  useBaseProvider?: boolean;
  useCustomTooltip?: boolean;
}
