'use strict';

import { RENDER_IF_FEATURE } from './renderIfFeature.component';
import { COPY_TO_CLIPBOARD_COMPONENT } from './clipboard/copyToClipboard.component';
import { TIME_FORMATTERS } from './timeFormatters';
import { SELECT_ON_DOUBLE_CLICK_DIRECTIVE } from 'core/utils/selectOnDblClick.directive';
import { UIB_MODAL_REJECTIONS } from './uibModalRejections';

const angular = require('angular');

module.exports = angular.module('spinnaker.utils', [
  COPY_TO_CLIPBOARD_COMPONENT,
  TIME_FORMATTERS,
  SELECT_ON_DOUBLE_CLICK_DIRECTIVE,
  require('./infiniteScroll.directive').name,
  RENDER_IF_FEATURE,
  UIB_MODAL_REJECTIONS,
  require('./waypoints/waypoint.directive').name,
  require('./waypoints/waypointContainer.directive').name,
]);
