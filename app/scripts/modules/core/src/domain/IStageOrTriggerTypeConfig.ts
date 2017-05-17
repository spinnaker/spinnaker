import { IValidatorConfig } from '../pipeline/config/validation/pipelineConfig.validator';

export interface IStageOrTriggerTypeConfig {
  manualExecutionHandler?: string;
  label: string;
  description: string;
  key: string;
  templateUrl: string;
  controller: string;
  controllerAs: string;
  validators: IValidatorConfig[];
}
