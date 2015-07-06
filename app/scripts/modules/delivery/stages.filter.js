'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.delivery.stages.filter', [])
  .filter('stages', function() {
    return function(stages, filter) {
      return stages.filter(function(stage) {
        return filter.stage.name[stage.name] &&
          filter.stage.status[stage.status.toLowerCase()] ||
          (filter.stage.scale === 'fixed' &&
             stage.status.toLowerCase() === 'not_started' &&
             filter.stage.name[stage.name]);
      });
    };
  }).name;
