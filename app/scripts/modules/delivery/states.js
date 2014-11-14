'use strict';

angular.module('deckApp.delivery')
  .constant('deliveryStates', {
    executions: {
      name: 'executions',
      url: '/executions',
      views: {
        'insight': {
          templateUrl: 'scripts/modules/delivery/pipelineExecutions.html',
          controller: 'pipelineExecutions as ctrl',
        },
      },
      children: [
        {
          name: 'execution',
          url: '/:executionId',
          view: {},
        },
      ],
    },
  });
