'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.delivery.states', [])
  .constant('deliveryStates', {
    executions: {
      name: 'executions',
      url: '/executions',
      views: {
        'insight': {
          template: '<executions application="application"></executions>',
        },
      },
      children: [
        {
          name: 'execution',
          url: '/:executionId?stage&step&details',
          params: {
            stage: {
              value: '0',
            },
            step: {
              value: '0',
            }
          },
          data: {
            pageTitleDetails: {
              title: 'Execution Details',
              nameParam: 'executionId'
            }
          }
        },
      ],
      data: {
        pageTitleSection: {
          title: 'Pipeline Executions'
        }
      }
    },
    configure: {
      name: 'pipelineConfig',
      url: '/executions/configure/:pipelineId',
      views: {
        'insight': {
          templateUrl: require('../pipeline/config/pipelineConfig.html'),
          controller: 'PipelineConfigCtrl',
          controllerAs: 'vm',
        },
      },
      data: {
        pageTitleSection: {
          title: 'pipeline config'
        }
      }
    }
  });
