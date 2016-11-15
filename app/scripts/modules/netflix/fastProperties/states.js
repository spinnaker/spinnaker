'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.states', [
    require('core/navigation/states.provider.js'),
  ])
  .config(function(statesProvider) {
    var fastPropertyRollouts = {
      name: 'rollouts',
      url: '/rollouts',
      views: {
        'master': {
          templateUrl: require('./dataNav/fastPropertyRollouts.html'),
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

    var appFastPropertyDetails = {
      name: 'propertyDetails',
      url: '/:propertyId',
      views: {
        'detail@../propInsights': {
          templateUrl: require('./fastPropertyDetails.html'),
          controller: 'FastPropertiesDetailsController',
          controllerAs: 'details'
        }
      },
      resolve: {
        fastProperty: ['$stateParams', function($stateParams) {
          return {
            propertyId: $stateParams.propertyId,
          };
        }]
      },
      data: {
        pageTitleDetails: {
          title: 'Fast Property Details',
          propertyId: 'propertyId',
          accountParam: 'accountId',
          regionParam: 'region'
        },
        history: {
          type: 'properties',
        },
      }
    };


    var mainProperty = {
      name: 'properties',
      url: '/properties',
      views: {
        'master': {
          templateUrl: require('./applicationProperties.html'),
          controller: 'ApplicationPropertiesController',
          controllerAs: 'fp'
        },
      },
      children: [
        appFastPropertyDetails
      ]
    };

    var appFastProperties = {
      name: 'propInsights',
      abstract: true,
      views: {
        'insight': {
          templateUrl: require('./mainApplicationProperties.html'),
          controller: 'ApplicationPropertiesController',
          controllerAs: 'fp'
        }
      },
      data: {
        pageTitleSection: {
          title: 'Fast Properties'
        }
      },
      children: [
        mainProperty
      ]
    };

    var globalFastPropertyDetails = {
      name: 'globalFastPropertyDetails',
      url: '/:propertyId',
      reloadOnSearch: true,
      views: {
        'detail@../data': {
          templateUrl: require('./globalFastPropertyDetails.html'),
          controller: 'GlobalFastPropertiesDetailsController',
          controllerAs: 'details'
        }
      },
      resolve: {
        fastProperty: ['$stateParams', function ($stateParams) {
          return {
            propertyId: $stateParams.propertyId,
          };
        }]
      },
      data: {
        pageTitleDetails: {
          title: 'Fast Property Details',
          propertyId: 'propertyId',
          accountParam: 'accountId',
          regionParam: 'region'
        },
        history: {
          type: 'properties',
        },
      }
    };


    var fastProperties = {
      name: 'properties',
      url: '/properties?q&group&app&env&stack&region',
      reloadOnSearch: false,
      views: {
        'master': {
          templateUrl: require('./dataNav/properties.html'),
          controller: 'FastPropertiesController',
          controllerAs: 'fp'
        }
      },
      children: [
        globalFastPropertyDetails
      ]
    };

    var data = {
      name: 'data',
      url: '/data',
      reloadOnSearch: false,
      views: {
        'main@': {
          templateUrl: require('./dataNav/main.html'),
          controller: 'FastPropertiesController',
          controllerAs: 'fp'
        }
      },
      data: {
        pageTitleMain: {
          label: 'Properties'
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
