import { IArtifact } from './IArtifact';
import { IExecution } from './IExecution';
import { IExpectedArtifact } from './IExpectedArtifact';
import { ITrigger } from './ITrigger';

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
