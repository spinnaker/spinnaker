import * as React from 'react';
import { IStageOrTriggerTypeConfig } from './IStageOrTriggerTypeConfig';
import { ITrigger } from './ITrigger';

export interface IExecutionTriggerStatusComponentProps {
  trigger: ITrigger;
}

export interface ITriggerTypeConfig extends IStageOrTriggerTypeConfig {
  executionStatusComponent?: React.ComponentType<IExecutionTriggerStatusComponentProps>;
  executionTriggerLabel?: (trigger: ITrigger) => string;
}
