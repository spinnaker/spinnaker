'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.states', [
    require('../../core/navigation/states.provider.js'),
  ])
  .config(function(statesProvider) {
    var fastPropertyRollouts = {
      name: 'rollouts',
      url: '/rollouts',
      views: {
        'master': {
          templateUrl: require('./fastPropertyRollouts.html'),
          controller: 'FastPropertyRolloutController',
          controllerAs: 'rollout'
        }
      },
      data: {
        pageTitleSection: {
          title: 'Fast Property Rollout'
        }
      }
    };

    var appFastProperties = {
      name: 'properties',
      url: '/properties',
      views: {
        'insight': {
          templateUrl: require('./applicationProperties.html'),
          controller: 'ApplicationPropertiesController',
          controllerAs: 'fp'
        }
      },
      data: {
        pageTitleSection: {
          title: 'Fast Properties'
        }
      }
    };

    var fastProperties = {
      name: 'properties',
      url: '/properties',
      reloadOnSearch: false,
      views: {
        'master': {
          templateUrl: require('./properties.html'),
          controller: 'FastPropertiesController',
          controllerAs: 'fp'
        }
      }
    };

    var data = {
      name: 'data',
      url: '/data',
      reloadOnSearch: false,
      views: {
        'main@': {
          templateUrl: require('./main.html'),
          controller: 'FastPropertyDataController',
          controllerAs: 'data'
        }
      },
      data: {
        pageTitleMain: {
          label: 'Data'
        }
      },
      children: [
        fastProperties,
        fastPropertyRollouts,
      ]
    };

    statesProvider.addStateConfig({ parent: 'application', state: fastPropertyRollouts });
    statesProvider.addStateConfig({ parent: 'application', state: appFastProperties });
    statesProvider.addStateConfig({ parent: 'home', state: data });
  });
