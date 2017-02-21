'use strict';

import {AUTHENTICATION} from 'core/authentication/authentication.module';
import {COPY_STAGE_MODAL_CONTROLLER} from './config/copyStage/copyStage.modal.controller';

let angular = require('angular');

require('./pipelines.less');

module.exports = angular.module('spinnaker.core.pipeline', [
  require('exports-loader?"ui.sortable"!angular-ui-sortable'),
  require('./config/pipelineConfig.module.js'),
  AUTHENTICATION,
  COPY_STAGE_MODAL_CONTROLLER,
  require('../notification/notifications.module.js'),
]);
