import { IAuthentication } from './IAuthentication';
import { IEntityTags } from './IEntityTags';
import { IExecutionStage, IExecutionStageSummary } from './IExecutionStage';
import { IExecutionTrigger } from './IExecutionTrigger';
import { IOrchestratedItem } from './IOrchestratedItem';
import { IPipeline } from './IPipeline';

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
  limitConcurrent?: boolean;
  name?: string;
  pipelineConfigId?: string;
  pipelineConfig?: IPipeline;
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
