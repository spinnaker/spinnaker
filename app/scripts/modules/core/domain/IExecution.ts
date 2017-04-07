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
  name?: string;
  executionEngine?: string;
  stringVal?: string;
  isComplete?: boolean;
  graphStatusHash?: string;
  pipelineConfigId?: string;
  searchField?: string;
  appConfig?: any;
}
