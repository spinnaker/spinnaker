import {module} from 'angular';
import {StateParams} from 'angular-ui-router';

import {STATE_CONFIG_PROVIDER, INestedState, StateConfigProvider} from 'core/navigation/state.provider';
import {
  APPLICATION_STATE_PROVIDER, ApplicationStateProvider,
} from 'core/application/application.state.provider';

export const FAST_PROPERTY_STATES = 'spinnaker.netflix.fastProperties.states';
module(FAST_PROPERTY_STATES, [
  APPLICATION_STATE_PROVIDER,
  STATE_CONFIG_PROVIDER,
]).config((applicationStateProvider: ApplicationStateProvider, stateConfigProvider: StateConfigProvider) => {

  const globalFastPropertyRolloutExecutionDetails: INestedState = {
    name: 'execution',
    url: '/:executionId?refId&stage&step&details',
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
  };

  const globalFastPropertyRollouts: INestedState = {
    name: 'executions',
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
    },
    children: [globalFastPropertyRolloutExecutionDetails]

  };

  const applicationFastPropertyDetails: INestedState = {
    name: 'propertyDetails',
    url: '/:propertyId',
    reloadOnSearch: true,
    views: {
      'detail@../propInsights': {
        templateUrl: require('./view/fastPropertyDetails.html'),
        controller: 'FastPropertiesDetailsController',
        controllerAs: 'details'
      }
    },
    resolve: {
      fastProperty: ['$stateParams', ($stateParams: StateParams) => {
        return {
          propertyId: $stateParams['propertyId'],
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


  const applicationFastProperty: INestedState = {
    name: 'properties',
    url: '/properties',
    reloadOnSearch: false,
    views: {
      'master': {
        templateUrl: require('./view/properties.html'),
        controller: 'FastPropertiesController',
        controllerAs: 'fp'
      },
    },
    children: [
      applicationFastPropertyDetails
    ]
  };

  const propInsights: INestedState = {
    name: 'propInsights',
    abstract: true,
    views: {
      'insight': {
        templateUrl: require('./mainApplicationProperties.html'),
      }
    },
    data: {
      pageTitleSection: {
        title: 'Fast Properties'
      }
    },
    children: [
      applicationFastProperty
    ]
  };

  const globalFastPropertyDetails: INestedState = {
    name: 'globalFastPropertyDetails',
    url: '/:propertyId',
    reloadOnSearch: true,
    views: {
      'detail@../data': {
        templateUrl: require('./view/fastPropertyDetails.html'),
        controller: 'FastPropertiesDetailsController',
        controllerAs: 'details'
      }
    },
    resolve: {
      fastProperty: ['$stateParams', ($stateParams: StateParams) => {
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


  const globalFastProperties: INestedState = {
    name: 'properties',
    url: '/properties',
    reloadOnSearch: false,
    views: {
      'master': {
        templateUrl: require('./view/properties.html'),
        controller: 'FastPropertiesController',
        controllerAs: 'fp'
      }
    },
    children: [
      globalFastPropertyDetails
    ]
  };

  const data: INestedState = {
    name: 'data',
    url: '/data',
    reloadOnSearch: false,
    views: {
      'main@': {
        templateUrl: require('./dataNav/main.html'),
      }
    },
    data: {
      pageTitleMain: {
        label: 'Properties'
      }
    },
    resolve: {
      app: (): any => {
        return null;
      }
    },
    children: [
      globalFastProperties,
      globalFastPropertyRollouts,
    ]
  };

  applicationStateProvider.addChildState(globalFastPropertyRollouts);
  applicationStateProvider.addChildState(propInsights);
  stateConfigProvider.addToRootState(data);
});
