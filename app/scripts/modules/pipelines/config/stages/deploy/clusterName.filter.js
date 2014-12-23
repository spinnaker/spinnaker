'use strict';

angular.module('deckApp.pipelines')
  .filter('clusterName', function(serverGroupService) {
    return function(input) {
      if (!input) {
        return 'n/a';
      }
      return serverGroupService.getClusterName(input.application, input.stack, input.freeFormDetails);
    };
  });
