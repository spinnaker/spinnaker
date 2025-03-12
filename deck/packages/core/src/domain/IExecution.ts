import type { IAuthentication } from './IAuthentication';
import type { IEntityTags } from './IEntityTags';
import type { IExecutionStage, IExecutionStageSummary } from './IExecutionStage';
import type { IExecutionTrigger } from './IExecutionTrigger';
import type { IOrchestratedItem } from './IOrchestratedItem';
import type { IPipeline } from './IPipeline';

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
