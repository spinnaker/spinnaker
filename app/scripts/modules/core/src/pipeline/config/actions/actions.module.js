'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.actions', [
  require('./rename/rename.module').name,
  require('./history/showHistory.controller').name,
  require('./enable/enable.module').name,
  require('./lock/lock.module').name,
  require('./unlock/unlock.module').name,
]);
