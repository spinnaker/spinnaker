import * as React from 'react';

import { ITriggerTemplateComponentProps } from '../pipeline/manualExecution/TriggerTemplate';
import { IValidatorConfig } from '../pipeline/config/validation/PipelineConfigValidator';
import { IContextualValidator } from 'core/pipeline';

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
  validateFn?: IContextualValidator;
}
