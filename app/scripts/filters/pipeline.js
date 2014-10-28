'use strict';

angular.module('deckApp')
  .filter('pipelineFilter', function() {
    return function(pipelines) {
      return pipelines.reduce(function(acc, pipeline) {
        if (acc[pipeline.name]) {
          acc[pipeline.name].push(pipeline);
        } else {
          acc[pipeline.name] = [pipeline];
        }
        return acc;
      }, {
        id: 1,
      }); 
    };
  });
