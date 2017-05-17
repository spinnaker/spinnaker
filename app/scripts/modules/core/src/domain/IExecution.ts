import {IOrchestratedItem} from './IOrchestratedItem';
import {IExecutionTrigger} from './IExecutionTrigger';
import { IExecutionStage, IExecutionStageSummary } from './IExecutionStage';

export interface IExecution extends IOrchestratedItem {
  id: string;
  trigger: IExecutionTrigger;
  stages: IExecutionStage[];
  context?: { [key: string]: any };
  user: string;
  application: string;
  deploymentTargets: string[];
  stageSummaries?: IExecutionStageSummary[];
  isStrategy?: boolean;
  name?: string;
  executionEngine?: string;
  stringVal?: string;
  isComplete?: boolean;
  graphStatusHash?: string;
  pipelineConfigId?: string;
  searchField?: string;
  appConfig?: any;
  cancellationReason?: string;
  canceledBy?: string;
  parallel?: boolean;
  buildInfo?: any;
}

export interface IExecutionGroup {
  heading: string;
  executions: IExecution[];
  config?: any;
  runningExecutions?: IExecution[];
  targetAccounts?: string[];
}

