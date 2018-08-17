import { IExecution } from './IExecution';
import { IArtifact } from './IArtifact';
import { IExpectedArtifact } from './IExpectedArtifact';

export interface IExecutionTrigger {
  buildInfo?: any;
  isPipeline?: boolean;
  parameters?: { [key: string]: string };
  parentExecution?: IExecution;
  parentPipelineApplication?: string;
  parentPipelineId?: string;
  parentPipelineName?: string;
  type: string;
  user: string;
  dryRun?: boolean;
  artifacts?: IArtifact[];
  resolvedExpectedArtifacts?: IExpectedArtifact[];
  link?: string;
  linkText?: string;
}
