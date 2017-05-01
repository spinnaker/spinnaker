import {module} from 'angular';

import {CONFIGURE_PIPELINE_TEMPLATE_MODAL_CTRL} from './configurePipelineTemplateModal.controller'
import {LIST_INPUT} from './ListInput';
import {NUMBER_INPUT} from './NumberInput';
import {OBJECT_INPUT} from './ObjectInput';
import {STRING_INPUT} from './StringInput';
import {VARIABLE} from './variable.component';
import {VARIABLE_INPUT_SERVICE} from './variableInput.service';

export const PIPELINE_TEMPLATE_MODULE = 'spinnaker.core.pipelineTemplate.module';
module(PIPELINE_TEMPLATE_MODULE, [
  CONFIGURE_PIPELINE_TEMPLATE_MODAL_CTRL,
  LIST_INPUT,
  NUMBER_INPUT,
  OBJECT_INPUT,
  STRING_INPUT,
  VARIABLE,
  VARIABLE_INPUT_SERVICE,
]);
