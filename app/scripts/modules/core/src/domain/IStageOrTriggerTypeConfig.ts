import React from 'react';

import { IContextualValidator } from '../pipeline';

import { IValidatorConfig } from '../pipeline/config/validation/PipelineConfigValidator';
import { ITriggerTemplateComponentProps } from '../pipeline/manualExecution/TriggerTemplate';

export interface IStageOrTriggerTypeConfig {
  manualExecutionComponent?: React.ComponentType<ITriggerTemplateComponentProps>;
  label?: string;
  description?: string;
  extendedDescription?: string;
  key: string;
  templateUrl?: string;
  controller?: string;
  controllerAs?: string;
  component?: React.ComponentType<any>;
  providesRepositoryInformation?: boolean;
  providesVersionForBake?: boolean;
  validators?: IValidatorConfig[];
  validateFn?: IContextualValidator;
}
