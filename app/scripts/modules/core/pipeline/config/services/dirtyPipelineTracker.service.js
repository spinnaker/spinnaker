'use strict';

let angular = require('angular');

/**
 * Simple registry to track pipelines that have been changed without being saved
 */
module.exports = angular.module('spinnaker.core.pipeline.config.services.dirtyTracker', [
])
  .factory('dirtyPipelineTracker', function() {

    var dirtyPipelines = [];

    function add(name) {
      if (dirtyPipelines.indexOf(name) === -1) {
        dirtyPipelines.push(name);
      }
    }

    function remove(name) {
      var index = dirtyPipelines.indexOf(name);
      if (index !== -1) {
        dirtyPipelines.splice(index, 1);
      }
    }

    function list() {
      return angular.copy(dirtyPipelines);
    }

    function hasDirtyPipelines() {
      return dirtyPipelines.length > 0;
    }

    return {
      add: add,
      remove: remove,
      list: list,
      hasDirtyPipelines: hasDirtyPipelines,
    };
  }
);
