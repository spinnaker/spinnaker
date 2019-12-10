'use strict';

import { BUTTON_BUSY_INDICATOR_COMPONENT } from './buttonBusyIndicator/buttonBusyIndicator.component';
import { NUMBER_LIST_COMPONENT } from './numberList/numberList.component';

const angular = require('angular');

export const CORE_FORMS_FORMS_MODULE = 'spinnaker.core.forms';
export const name = CORE_FORMS_FORMS_MODULE; // for backwards compatibility
angular.module(CORE_FORMS_FORMS_MODULE, [
  require('./autofocus/autofocus.directive').name,
  require('./checklist/checklist.directive').name,
  require('./checkmap/checkmap.directive').name,
  require('./ignoreEmptyDelete.directive').name,
  BUTTON_BUSY_INDICATOR_COMPONENT,
  require('./mapEditor/mapEditor.component').name,
  require('./validateOnSubmit/validateOnSubmit.directive').name,
  NUMBER_LIST_COMPONENT,
]);
