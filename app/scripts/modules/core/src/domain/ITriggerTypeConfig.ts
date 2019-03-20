import * as React from 'react';
import { IStageOrTriggerTypeConfig } from './IStageOrTriggerTypeConfig';
import { ITrigger } from './ITrigger';
import { IAuthentication } from './IAuthentication';

export interface IExecutionTriggerStatusComponentProps {
  trigger: ITrigger;
  authentication: IAuthentication;
}

export interface ITriggerTypeConfig extends IStageOrTriggerTypeConfig {
  executionStatusComponent?: React.ComponentType<IExecutionTriggerStatusComponentProps>;
  executionTriggerLabel?: (trigger: ITrigger) => string;
  excludedArtifactTypePatterns?: RegExp[];
}
