'use strict';

const angular = require('angular');

import {CREATE_PIPELINE_MODAL} from './createPipelineModal.component';

module.exports = angular.module('spinnaker.core.pipeline.config.actions.create', [
  require('./createPipelineButton.controller.js').name,
  require('./createPipelineButton.directive.js').name,
  CREATE_PIPELINE_MODAL,
]);
