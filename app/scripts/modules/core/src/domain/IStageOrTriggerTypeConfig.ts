import * as React from 'react';

import { ITriggerTemplateComponentProps } from '../pipeline/manualExecution/TriggerTemplate';
import { IValidatorConfig } from '../pipeline/config/validation/PipelineConfigValidator';

export interface IStageOrTriggerTypeConfig {
  manualExecutionComponent?: React.ComponentType<ITriggerTemplateComponentProps>;
  label?: string;
  description?: string;
  key: string;
  templateUrl?: string;
  controller?: string;
  controllerAs?: string;
  component?: React.ComponentType;
  providesVersionForBake?: boolean;
  validators?: IValidatorConfig[];
}
