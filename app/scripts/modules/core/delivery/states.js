'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.delivery.states', [])
  .constant('deliveryStates', {
    pipelines: {
      name: 'pipelines',
      url: '/executions',
      abstract: true,
      views: {
        'insight': {
          template: '<div ui-view="pipelines" class="flex-fill"></div>'
        }
      },
      children: [
        {
          name: 'executions',
          url: '',
          views: {
            'pipelines': {
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
        {
          name: 'pipelineConfig',
          url: '/configure/:pipelineId',
          views: {
            'pipelines': {
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
        },
        {
          name: 'executionDetails',
          url: '/details',
          views: {
            'pipelines': {
              templateUrl: require('./details/singleExecutionDetails.html'),
              controller: 'SingleExecutionDetailsCtrl',
              controllerAs: 'vm',
            },
          },
          abstract: true,
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
                },
              },
              data: {
                pageTitleDetails: {
                  title: 'Execution Details',
                  nameParam: 'executionId'
                }
              }
            },
          ],
        }
      ],
    },
  });
