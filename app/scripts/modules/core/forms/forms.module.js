'use strict';

import numberListModule from './numberList/numberList.component';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.forms', [
  require('./autofocus/autofocus.directive.js'),
  require('./checklist/checklist.directive.js'),
  require('./checkmap/checkmap.directive.js'),
  require('./ignoreEmptyDelete.directive.js'),
  require('./buttonBusyIndicator/buttonBusyIndicator.directive.js'),
  require('./mapEditor/mapEditor.component.js'),
  numberListModule,
]);
