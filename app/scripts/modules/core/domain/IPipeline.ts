import {IStage} from './IStage';
import {ITrigger} from './ITrigger';

export interface IPipeline {
  application: string;
  executionEngine: string;
  id: string;
  index: number;
  isNew?: boolean;
  keepWaitingPipelines: boolean;
  lastModifiedBy?: string;
  locked?: boolean;
  limitConcurrent: boolean;
  name: string;
  parallel: boolean;
  stages: IStage[];
  strategy: boolean;
  triggers: ITrigger[];
  parameterConfig: IParameter[];
}

export interface IParameter {
  name: string;
  description: string;
  'default': string;
  hasOptions: boolean;
  options: IParameterOption[];
}

export interface IParameterOption {
  value: string;
}
