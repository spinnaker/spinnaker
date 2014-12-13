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
          data: {
            pageTitleSection: {
              nameParam: 'executionId'
            }
          }
        },
      ],
      data: {
        pageTitleSection: {
          title: 'pipeline executions'
        }
      }
    },
    configure: {
      name: 'pipelineConfig',
      url: '/pipelines',
      views: {
        'insight': {
          templateUrl: 'scripts/modules/pipelines/config/pipelineConfig.html',
          controller: 'PipelineConfigCtrl as pipelineConfigCtrl'
        },
      },
      data: {
        pageTitleSection: {
          title: 'pipeline config'
        }
      }
    }
  });
