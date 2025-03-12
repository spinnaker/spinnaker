import type { IArtifact } from './IArtifact';
import type { IExecution } from './IExecution';
import type { IExpectedArtifact } from './IExpectedArtifact';
import type { ITrigger } from './ITrigger';

export interface IExecutionTrigger extends ITrigger {
  buildInfo?: any;
  isPipeline?: boolean;
  parameters?: { [key: string]: string };
  parentExecution?: IExecution;
  parentPipelineApplication?: string;
  parentPipelineId?: string;
  parentPipelineStageId?: string;
  parentPipelineName?: string;
  type: string;
  user: string;
  dryRun?: boolean;
  artifacts?: IArtifact[];
  resolvedExpectedArtifacts?: IExpectedArtifact[];
  link?: string;
  linkText?: string;
}
