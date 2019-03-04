import { IEntityTags } from './IEntityTags';
import { IOrchestratedItem } from './IOrchestratedItem';
import { IExecutionTrigger } from './IExecutionTrigger';
import { IExecutionStage, IExecutionStageSummary } from './IExecutionStage';
import { IAuthentication } from './IAuthentication';

export interface IExecution extends IOrchestratedItem {
  appConfig?: any;
  application: string;
  authentication: IAuthentication;
  buildInfo?: any;
  buildTime?: number;
  canceledBy?: string;
  cancellationReason?: string;
  context?: { [key: string]: any };
  currentStages?: IExecutionStageSummary[];
  deploymentTargets: string[];
  // expandedGroups?: {[groupId: string]: boolean};
  entityTags?: IEntityTags;
  graphStatusHash?: string;
  id: string;
  isComplete?: boolean;
  isStrategy?: boolean;
  name?: string;
  pipelineConfigId?: string;
  searchField?: string;
  stageSummaries?: IExecutionStageSummary[]; // added by transformer
  stageWidth?: string; // added by transformer
  stages: IExecutionStage[];
  stringVal?: string;
  trigger: IExecutionTrigger;
  user: string;
  fromTemplate?: boolean;
  hydrated?: boolean;
  hydrator?: Promise<IExecution>;
}

export interface IExecutionGroup {
  config?: any;
  executions: IExecution[];
  heading: string;
  runningExecutions?: IExecution[];
  targetAccounts?: string[];
  fromTemplate?: boolean;
}
