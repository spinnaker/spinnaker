'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.utils', [
  require('./appendTransform.js'),
  require('./d3.js'),
  require('./isEmpty.js'),
  require('./jQuery.js'),
  require('../utils/lodash.js'),
  require('./moment.js'),
  require('./scrollTriggerService.js'),
  require('./selectOnDblClick.directive.js'),
  require('./rx.js'),
  require('./stickyHeader/stickyHeader.directive.js'),
  require('./timeFormatters.js'),
]);
