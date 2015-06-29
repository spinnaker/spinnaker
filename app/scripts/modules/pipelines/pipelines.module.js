'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines', [
  require('./config/pipelineConfigurer.js'),
  require('./config/pipelineConfigProvider.js'),
  require('./config/pipelineConfig.controller.js'),
  require('./config/actions/create/createPipelineModal.controller.js'),
  require('./config/validation/pipelineConfigValidator.directive.js'),
  require('./config/services/dirtyPipelineTracker.service.js'),
  require('../caches/viewStateCache.js'),

  require('restangular'),
  require('./config/stages/stage.module.js'),
  require('./config/triggers/trigger.module.js'),
  require('./config/parameters/pipeline.module.js'),
  require('./config/actions/create/createPipeline.module.js'),
  require('./config/actions/delete/deletePipeline.module.js'),
  require('./config/actions/enableParallel/enableParallel.controller.js'),
  require('./config/actions/disableParallel/disableParallel.controller.js'),
  require('./config/actions/rename/renamePipeline.module.js'),
  require('./config/actions/json/editPipelineJson.module.js'),
  require('../authentication/authentication.module.js'),
  require('../utils/lodash.js'),
  require('../caches/deckCacheFactory.js'),
  'ui.sortable',

  require('./config/graph/pipeline.graph.directive.js'),
]);
