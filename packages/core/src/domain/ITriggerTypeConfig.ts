import type React from 'react';

import type { IAuthentication } from './IAuthentication';
import type { IStageOrTriggerTypeConfig } from './IStageOrTriggerTypeConfig';
import type { ITrigger } from './ITrigger';

export interface IExecutionTriggerStatusComponentProps {
  trigger: ITrigger;
  authentication: IAuthentication;
}

export interface ITriggerTypeConfig extends IStageOrTriggerTypeConfig {
  executionStatusComponent?: React.ComponentType<IExecutionTriggerStatusComponentProps>;
  executionTriggerLabel?: (trigger: ITrigger) => string;
  excludedArtifactTypePatterns?: RegExp[];
}
