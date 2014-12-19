'use strict';

angular.module('deckApp.delivery')
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
  });
