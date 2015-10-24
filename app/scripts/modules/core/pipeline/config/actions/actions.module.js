'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.actions', [
  require('./delete/delete.module.js'),
  require('./json/editPipelineJson.module.js'),
  require('./rename/rename.module.js'),
  require('./enableParallel/enableParallel.controller.js'),
  require('./disableParallel/disableParallel.controller.js'),
  require('../../../../netflix/migrator/pipeline/pipeline.migrator.directive.js'), // TODO: this is a problem
]).name;
