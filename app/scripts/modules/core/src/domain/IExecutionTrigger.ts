import {IExecution} from './IExecution';

export interface IExecutionTrigger {
  buildInfo?: any;
  isPipeline?: boolean;
  parameters?: { [key: string]: string; };
  parentExecution?: IExecution;
  parentPipelineApplication?: string;
  parentPipelineId?: string;
  parentPipelineName?: string;
  type: string;
  user: string;
}
