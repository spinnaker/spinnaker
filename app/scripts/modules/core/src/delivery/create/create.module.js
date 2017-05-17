'use strict';

const angular = require('angular');

import {CREATE_PIPELINE_MODAL} from './createPipelineModal.component';

module.exports = angular.module('spinnaker.core.pipeline.config.actions.create', [
  require('./createPipelineButton.controller.js'),
  require('./createPipelineButton.directive.js'),
  CREATE_PIPELINE_MODAL,
]);
