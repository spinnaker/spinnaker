'use strict';

import { BUTTON_BUSY_INDICATOR_COMPONENT } from './buttonBusyIndicator/buttonBusyIndicator.component';
import { NUMBER_LIST_COMPONENT } from './numberList/numberList.component';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.forms', [
  require('./autofocus/autofocus.directive').name,
  require('./checklist/checklist.directive').name,
  require('./checkmap/checkmap.directive').name,
  require('./ignoreEmptyDelete.directive').name,
  BUTTON_BUSY_INDICATOR_COMPONENT,
  require('./mapEditor/mapEditor.component').name,
  require('./validateOnSubmit/validateOnSubmit.directive').name,
  NUMBER_LIST_COMPONENT,
]);
