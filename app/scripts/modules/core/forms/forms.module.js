'use strict';

import {BUTTON_BUSY_INDICATOR_COMPONENT} from './buttonBusyIndicator/buttonBusyIndicator.component';
import {NUMBER_LIST_COMPONENT} from './numberList/numberList.component';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.forms', [
  require('./autofocus/autofocus.directive.js'),
  require('./checklist/checklist.directive.js'),
  require('./checkmap/checkmap.directive.js'),
  require('./ignoreEmptyDelete.directive.js'),
  BUTTON_BUSY_INDICATOR_COMPONENT,
  require('./mapEditor/mapEditor.component.js'),
  require('./validateOnSubmit/validateOnSubmit.directive.js'),
  NUMBER_LIST_COMPONENT
]);
