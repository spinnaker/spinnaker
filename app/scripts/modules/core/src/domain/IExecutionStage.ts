import { Application } from 'core/application/application.model';

import { IExecution } from './IExecution';
import { IOrchestratedItem } from './IOrchestratedItem';
import { IStage } from './IStage';
import { IStageStep } from './IStageStep';

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
  tasks: IStageStep[];
}

export interface IExecutionStageLabelComponentProps {
  application?: Application;
  execution?: IExecution;
  executionMarker?: boolean;
  stage: IExecutionStageSummary;
}

export interface IExecutionStageSummary extends IOrchestratedItem {
  after: IExecutionStage[];
  before: IExecutionStage[];
  cloudProvider: string;
  color?: string;
  comments: string;
  endTime: number;
  extraLabelLines?: (stage: IExecutionStageSummary) => number;
  firstActiveStage?: number;
  id: string;
  inSuspendedExecutionWindow?: boolean;
  index: number;
  labelComponent?: React.ComponentClass<IExecutionStageLabelComponentProps>;
  markerIcon?: React.ComponentClass<{ stage: IExecutionStageSummary }>;
  masterStage: IExecutionStage;
  masterStageIndex: number;
  name: string;
  refId: number | string;
  requisiteStageRefIds: (number | string)[];
  stages: IExecutionStage[];
  startTime: number;
  status: string;
  type: string;
  useCustomTooltip?: boolean;
}
