import { IStage } from './IStage';
import { ITrigger } from './ITrigger';
import { IExpectedArtifact } from 'core/domain/IExpectedArtifact';

export interface IPipeline {
  application: string;
  description?: string;
  id: string;
  index: number;
  isNew?: boolean;
  keepWaitingPipelines: boolean;
  lastModifiedBy?: string;
  locked?: boolean;
  limitConcurrent: boolean;
  name: string;
  stages: IStage[];
  strategy: boolean;
  triggers: ITrigger[];
  parameterConfig: IParameter[];
  disabled?: boolean;
  expectedArtifacts?: IExpectedArtifact[];
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


export interface IPipelineCommand {
  pipeline: IPipeline;
  trigger: ITrigger;
  notificationEnabled: boolean;
  notification: {
    type: string;
    address: string;
    when: string[];
  }
  pipelineName: string;
}
