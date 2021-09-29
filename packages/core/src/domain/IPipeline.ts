import type { IEntityTags } from './IEntityTags';
import type { IExpectedArtifact } from './IExpectedArtifact';
import type { INotification } from './INotification';
import type { IStage } from './IStage';
import type { ITrigger } from './ITrigger';

export interface IPipeline {
  application: string;
  description?: string;
  entityTags?: IEntityTags;
  id: string;
  index?: number;
  isNew?: boolean;
  keepWaitingPipelines: boolean;
  lastModifiedBy?: string;
  locked?: IPipelineLock;
  limitConcurrent: boolean;
  manualStartAlert?: IPipelineManualStartAlert;
  migrationStatus?: string;
  name: string;
  notifications?: INotification[];
  respectQuietPeriod?: boolean;
  schema?: string;
  stages: IStage[];
  strategy?: boolean;
  triggers: ITrigger[];
  parameterConfig: IParameter[];
  disabled?: boolean;
  expectedArtifacts?: IExpectedArtifact[];
  roles?: any[];
  source?: {
    id: string;
    type: string;
  };
  type?: string;
  updateTs?: number;
  spelEvaluator?: string;
  tags?: IPipelineTag[];
}

export interface IPipelineManualStartAlert {
  type: 'danger' | 'warning' | 'info';
  message: string;
}

export interface IPipelineLock {
  ui: boolean;
  allowUnlockUi?: boolean;
  description?: string;
}

export interface IParameter extends ITemplateInheritable {
  name: string;
  conditional?: any;
  constraint?: string;
  description: string;
  default: string;
  hasOptions: boolean;
  label?: string;
  pinned: boolean;
  options: IParameterOption[];
  condition?: IParameterCondition;
  required?: boolean;
}

export interface IParameterCondition {
  parameter: string;
  comparator: '>' | '<' | '>=' | '<=' | '=' | '!=';
  comparatorValue: string | number;
}

export interface IParameterOption {
  value: string;
}

export interface IPipelineCommand<T = ITrigger> {
  dryRun?: boolean;
  extraFields?: { [key: string]: any };
  triggerInvalid?: boolean;
  pipeline: IPipeline;
  trigger: T;
  notificationEnabled: boolean;
  notification: INotification;
  parameters?: { [key: string]: any };
  pipelineName: string;
}

export interface IPipelineRef {
  application: string;
  name: string;
  id?: string;
}

export interface ITemplateInheritable {
  inherited?: boolean;
}

export interface IPipelineTag {
  name: string;
  value: string;
}
