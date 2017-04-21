import {IOrchestratedItem} from './IOrchestratedItem';
import {IStage} from './IStage';
import {IStageStep} from './IStageStep';

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
  masterStage: IStage;
  labelTemplate: React.ComponentClass<{ stage: IExecutionStageSummary }>;
  extraLabelLines?: (stage: IExecutionStageSummary) => number;
  index: number;
  status: string;
  hasNotStarted: boolean;
  firstActiveStage?: number;
}
