'use strict';

import {RENDER_IF_FEATURE} from './renderIfFeature.component';
import {STICKY_HEADER_DIRECTIVE} from './stickyHeader/stickyHeader.directive';
import {COPY_TO_CLIPBOARD_COMPONENT} from './clipboard/copyToClipboard.component';

let angular = require('angular');

module.exports = angular.module('spinnaker.utils', [
  require('./jQuery.js'),
  require('./moment.js'),
  require('./appendTransform.js'),
  COPY_TO_CLIPBOARD_COMPONENT,
  require('./timeFormatters.js'),
  require('./infiniteScroll.directive.js'),
  RENDER_IF_FEATURE,
  STICKY_HEADER_DIRECTIVE,
]);
