'use strict';

import {RENDER_IF_FEATURE} from './renderIfFeature.component';
import {STICKY_HEADER_COMPONENT} from './stickyHeader/stickyHeader.component';
import {COPY_TO_CLIPBOARD_COMPONENT} from './clipboard/copyToClipboard.component';
import {TIME_FORMATTERS} from './timeFormatters';

let angular = require('angular');

module.exports = angular.module('spinnaker.utils', [
  require('./jQuery.js'),
  require('./moment.js'),
  require('./appendTransform.js'),
  COPY_TO_CLIPBOARD_COMPONENT,
  TIME_FORMATTERS,
  require('./infiniteScroll.directive.js'),
  RENDER_IF_FEATURE,
  STICKY_HEADER_COMPONENT,
]);
