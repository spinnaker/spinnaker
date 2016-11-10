'use strict';

let angular = require('angular');

import {UUID_SERVICE} from './uuid.service';

module.exports = angular.module('spinnaker.utils', [
  require('./jQuery.js'),
  require('./moment.js'),
  UUID_SERVICE,
  require('./appendTransform.js'),
  require('./clipboard/copyToClipboard.directive.js'),
  require('./timeFormatters.js'),
  require('./infiniteScroll.directive.js'),
]);
