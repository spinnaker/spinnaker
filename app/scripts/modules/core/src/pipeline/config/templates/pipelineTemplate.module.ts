import {module} from 'angular';

import {CONFIGURE_PIPELINE_TEMPLATE_MODAL_CTRL} from './configurePipelineTemplateModal.controller'
import {LIST_INPUT} from './inputs/ListInput';
import {LIST_VALIDATOR} from './validators/list.validator';
import {NUMBER_INPUT} from './inputs/NumberInput';
import {NUMBER_VALIDATOR} from './validators/number.validator';
import {OBJECT_INPUT} from './inputs/ObjectInput';
import {OBJECT_VALIDATOR} from './validators/object.validator';
import {STRING_INPUT} from './inputs/StringInput';
import {STRING_VALIDATOR} from './validators/string.validator';
import {TEMPLATE_PLAN_ERRORS} from './templatePlanErrors.component';
import {VARIABLE} from './variable.component';
import {VARIABLE_INPUT_SERVICE} from './inputs/variableInput.service';
import {VARIABLE_VALIDATOR_SERVICE} from './validators/variableValidator.service';

export const PIPELINE_TEMPLATE_MODULE = 'spinnaker.core.pipelineTemplate.module';
module(PIPELINE_TEMPLATE_MODULE, [
  CONFIGURE_PIPELINE_TEMPLATE_MODAL_CTRL,
  LIST_INPUT,
  LIST_VALIDATOR,
  NUMBER_INPUT,
  NUMBER_VALIDATOR,
  OBJECT_INPUT,
  OBJECT_VALIDATOR,
  STRING_INPUT,
  STRING_VALIDATOR,
  TEMPLATE_PLAN_ERRORS,
  VARIABLE,
  VARIABLE_INPUT_SERVICE,
  VARIABLE_VALIDATOR_SERVICE,
]);
