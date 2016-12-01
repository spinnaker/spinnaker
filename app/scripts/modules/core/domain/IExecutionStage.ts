import {IOrchestratedItem} from './IOrchestratedItem';
import {IStageStep} from './IStageStep';

export interface IExecutionStage extends IOrchestratedItem {
  name: string;
  type: string;
  refId: string;
  tasks: IStageStep[];
}
