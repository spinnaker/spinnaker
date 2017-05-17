import {IExecution} from './IExecution';

export interface IExecutionTrigger {
  user: string;
  type: string;
  parentExecution?: IExecution;
  parentPipelineApplication?: string;
  parentPipelineId?: string;
  parentPipelineName?: string;
  parameters?: { [key: string]: string; };
}
