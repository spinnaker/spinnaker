import { IOrchestratedItem } from './IOrchestratedItem';
import { IExecutionTrigger } from './IExecutionTrigger';
import { IExecutionStage, IExecutionStageSummary } from './IExecutionStage';

export interface IExecution extends IOrchestratedItem {
  appConfig?: any;
  application: string;
  buildInfo?: any;
  canceledBy?: string;
  cancellationReason?: string;
  context?: { [key: string]: any };
  currentStages?: IExecutionStageSummary[];
  deploymentTargets: string[];
  graphStatusHash?: string;
  id: string;
  isComplete?: boolean;
  isStrategy?: boolean;
  name?: string;
  pipelineConfigId?: string;
  searchField?: string;
  stageSummaries?: IExecutionStageSummary[];
  stageWidth?: string;
  stages: IExecutionStage[];
  stringVal?: string;
  trigger: IExecutionTrigger;
  user: string;
}

export interface IExecutionGroup {
  config?: any;
  executions: IExecution[];
  heading: string;
  runningExecutions?: IExecution[];
  targetAccounts?: string[];
}

