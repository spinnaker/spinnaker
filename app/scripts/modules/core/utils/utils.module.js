'use strict';

import {RENDER_IF_FEATURE} from './renderIfFeature.component';
import {STICKY_HEADER_DIRECTIVE} from './stickyHeader/stickyHeader.directive';

let angular = require('angular');

module.exports = angular.module('spinnaker.utils', [
  require('./jQuery.js'),
  require('./moment.js'),
  require('./appendTransform.js'),
  require('./clipboard/copyToClipboard.directive.js'),
  require('./timeFormatters.js'),
  require('./infiniteScroll.directive.js'),
  RENDER_IF_FEATURE,
  STICKY_HEADER_DIRECTIVE,
]);
