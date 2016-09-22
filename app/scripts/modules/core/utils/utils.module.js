'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.utils', [
  require('./d3.js'),
  require('./jQuery.js'),
  require('./moment.js'),
  require('./uuid.service.js'),
  require('./appendTransform.js'),
  require('./clipboard/copyToClipboard.directive.js'),
  require('./timeFormatters.js'),
  require('./infiniteScroll.directive.js'),
]);
