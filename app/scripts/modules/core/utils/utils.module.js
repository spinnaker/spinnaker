'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.utils', [
  require('./d3.js'),
  require('./jQuery.js'),
  require('./lodash.js'),
  require('./moment.js'),
  require('./rx.js'),
  require('./uuid.service.js'),
  require('./appendTransform.js'),
  require('./clipboard/copyToClipboard.directive.js'),
  require('./timeFormatters.js'),
]);
