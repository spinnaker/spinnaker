import {IOrchestratedItem} from './IOrchestratedItem';
import {IStageStep} from './IStageStep';

export interface IRestartDetails {
  restartedBy: string;
  restartTime: number;
}

export interface IExecutionContext {
  restartDetails?: IRestartDetails;
  targetReferences?: any;
  instances?: any;
  asg?: string;
}

export interface IExecutionStage extends IOrchestratedItem {
  name: string;
  type: string;
  refId: string;
  tasks: IStageStep[];
  context: IExecutionContext;
}
