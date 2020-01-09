import React from 'react';

import { ITriggerTemplateComponentProps } from '../pipeline/manualExecution/TriggerTemplate';
import { IValidatorConfig } from '../pipeline/config/validation/PipelineConfigValidator';
import { IContextualValidator } from 'core/pipeline';

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
