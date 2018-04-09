'use strict';

import { BUTTON_BUSY_INDICATOR_COMPONENT } from './buttonBusyIndicator/buttonBusyIndicator.component';
import { NUMBER_LIST_COMPONENT } from './numberList/numberList.component';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.forms', [
  require('./autofocus/autofocus.directive.js').name,
  require('./checklist/checklist.directive.js').name,
  require('./checkmap/checkmap.directive.js').name,
  require('./ignoreEmptyDelete.directive.js').name,
  BUTTON_BUSY_INDICATOR_COMPONENT,
  require('./mapEditor/mapEditor.component.js').name,
  require('./validateOnSubmit/validateOnSubmit.directive.js').name,
  NUMBER_LIST_COMPONENT,
]);
