import {IOrchestratedItem} from './IOrchestratedItem';
import {IExecutionTrigger} from './IExecutionTrigger';
import {IExecutionStage} from './IExecutionStage';
import {IStageSummary} from './IStage';

export interface IExecution extends IOrchestratedItem {
  id: string;
  trigger: IExecutionTrigger;
  stages: IExecutionStage[];
  user: string;
  stageSummaries?: IStageSummary[];
  isStrategy?: boolean;
}
