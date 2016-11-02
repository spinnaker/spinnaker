'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.utils', [
  require('./jQuery.js'),
  require('./moment.js'),
  require('./uuid.service.js'),
  require('./appendTransform.js'),
  require('./clipboard/copyToClipboard.directive.js'),
  require('./timeFormatters.js'),
  require('./infiniteScroll.directive.js'),
]);
