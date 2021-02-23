import React from 'react';

import { IAuthentication } from './IAuthentication';
import { IStageOrTriggerTypeConfig } from './IStageOrTriggerTypeConfig';
import { ITrigger } from './ITrigger';

export interface IExecutionTriggerStatusComponentProps {
  trigger: ITrigger;
  authentication: IAuthentication;
}

export interface ITriggerTypeConfig extends IStageOrTriggerTypeConfig {
  executionStatusComponent?: React.ComponentType<IExecutionTriggerStatusComponentProps>;
  executionTriggerLabel?: (trigger: ITrigger) => string;
  excludedArtifactTypePatterns?: RegExp[];
}
