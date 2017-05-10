import { IStageOrTriggerTypeConfig } from './IStageOrTriggerTypeConfig';

export interface ITriggerTypeConfig extends IStageOrTriggerTypeConfig {
  manualExecutionHandler?: string;
}
