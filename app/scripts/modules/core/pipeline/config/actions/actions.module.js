'use strict';

const angular = require('angular');

import {EDIT_PIPELINE_JSON_MODAL_CONTROLLER} from './json/editPipelineJsonModal.controller';

module.exports = angular.module('spinnaker.core.pipeline.config.actions', [
  require('./delete/delete.module.js'),
  EDIT_PIPELINE_JSON_MODAL_CONTROLLER,
  require('./rename/rename.module.js'),
  require('./history/showHistory.controller'),
  require('./enable/enable.module'),
  require('./disable/disable.module'),
  require('./lock/lock.module'),
  require('./unlock/unlock.module'),
]);
