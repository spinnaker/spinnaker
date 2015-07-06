'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.delivery.states', [])
  .constant('deliveryStates', {
    executions: {
      name: 'executions',
      url: '/executions',
      views: {
        'insight': {
          templateUrl: require('./pipelineExecutions.html'),
          controller: 'pipelineExecutions as ctrl',
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
          views: {},
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
      url: '/pipelines',
      views: {
        'insight': {
          templateUrl: require('../pipelines/config/pipelineConfig.html'),
          controller: 'PipelineConfigCtrl as pipelineConfigCtrl'
        },
      },
      data: {
        pageTitleSection: {
          title: 'pipeline config'
        }
      }
    }
  })
  .name;
