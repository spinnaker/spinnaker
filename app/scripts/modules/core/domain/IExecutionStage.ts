import {IOrchestratedItem} from './IOrchestratedItem';
import {IStage} from './IStage';
import {IStageStep} from './IStageStep';
import {Application} from 'core/application/application.model';
import {IExecution} from './IExecution';

export interface IRestartDetails {
  restartedBy: string;
  restartTime: number;
}

export interface IExecutionContext {
  [key: string]: any;
  restartDetails?: IRestartDetails;
  targetReferences?: any;
  instances?: any;
  asg?: string;
}

export interface IExecutionStage extends IOrchestratedItem, IStage {
  id: string;
  tasks: IStageStep[];
  context: IExecutionContext;
}

export interface IExecutionStageSummary extends IExecutionStage {
  masterStage: IExecutionStage;
  stages: IExecutionStage[];
  labelComponent: React.ComponentClass<{ stage: IExecutionStageSummary, application?: Application, execution?: IExecution, executionMarker?: boolean }>;
  markerIcon: React.ComponentClass<{ stage: IExecutionStageSummary }>;
  extraLabelLines?: (stage: IExecutionStageSummary) => number;
  useCustomTooltip?: boolean;
  inSuspendedExecutionWindow?: boolean;
  index: number;
  status: string;
  hasNotStarted: boolean;
  firstActiveStage?: number;
}
