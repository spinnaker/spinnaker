'use strict';

angular.module('deckApp.delivery')
  .filter('stages', function() {
    return function(stages, filter) {
      return stages.filter(function(stage) {
        return filter.stage.name[stage.name] &&
          filter.stage.status[stage.normalizedStatus.toLowerCase()] ||
          (filter.stage.scale === 'fixed' &&
             stage.normalizedStatus.toLowerCase() === 'not_started' &&
             filter.stage.name[stage.name]);
      });
    };
  });
