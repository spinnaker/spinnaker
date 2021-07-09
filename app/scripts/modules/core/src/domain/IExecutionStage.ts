import { IExecution } from './IExecution';
import { IOrchestratedItem } from './IOrchestratedItem';
import { IStage } from './IStage';
import { IExecutionDetailsSection, IStageTypeConfig } from './IStageTypeConfig';
import { ITaskStep } from './ITaskStep';
import { Application } from '../application/application.model';

export interface IRestartDetails {
  restartedBy: string;
  restartTime: number;
}

export interface IExecutionContext {
  [key: string]: any;
  asg?: string;
  deploymentDetails?: any;
  instances?: any;
  restartDetails?: IRestartDetails;
  targetReferences?: any;
}

export interface IExecutionStage extends IOrchestratedItem, IStage {
  after?: IExecutionStage[];
  before?: IExecutionStage[];
  context: IExecutionContext;
  id: string;
  tasks: ITaskStep[];
}

export interface IExecutionStageLabelProps {
  application?: Application;
  execution?: IExecution;
  executionMarker?: boolean;
  stage: IExecutionStageSummary;
}

export interface IExecutionDetailsProps {
  application: Application;
  detailsSections: IExecutionDetailsSection[];
  execution: IExecution;
  provider: string;
  stage: IExecutionStage;
  config: IStageTypeConfig;
}

export interface IExecutionDetailsState {
  configSections: string[];
  currentSection: string;
}

export interface IExecutionStageSummary extends IOrchestratedItem {
  activeStageType?: string;
  after: IExecutionStage[];
  before: IExecutionStage[];
  cloudProvider: string;
  color?: string;
  comments: string;
  endTime: number;
  extraLabelLines?: (stage: IExecutionStageSummary) => number;
  firstActiveStage?: number;
  graphRowOverride?: number;
  group?: string;
  groupStages?: IExecutionStageSummary[];
  id: string;
  suspendedStageTypes: Set<string>;
  index: number;
  labelComponent?: React.ComponentType<IExecutionStageLabelProps>;
  markerIcon?: React.ComponentType<{ stage: IExecutionStageSummary }>;
  masterStage: IExecutionStage;
  masterStageIndex: number;
  name: string;
  refId: number | string;
  requisiteStageRefIds: Array<number | string>;
  stages: IExecutionStage[];
  startTime: number;
  status: string;
  type: string;
  useCustomTooltip?: boolean;
  requiresAttention?: boolean;
}
