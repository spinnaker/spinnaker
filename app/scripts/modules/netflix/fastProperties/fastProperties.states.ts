import {module} from 'angular';

import {STATE_CONFIG_PROVIDER, INestedState, StateConfigProvider} from 'core/navigation/state.provider';
import {
  APPLICATION_STATE_PROVIDER, ApplicationStateProvider,
  IApplicationStateParams
} from 'core/application/application.state.provider';

export interface IPropertyDetailsStateParams extends IApplicationStateParams {
  propertyId: string;
}

export interface IFastPropertiesStateParams extends IApplicationStateParams {

}

export const FAST_PROPERTY_STATES = 'spinnaker.netflix.tableau.states';
module(FAST_PROPERTY_STATES, [
  APPLICATION_STATE_PROVIDER,
  STATE_CONFIG_PROVIDER,
]).config((applicationStateProvider: ApplicationStateProvider, stateConfigProvider: StateConfigProvider) => {
  const fastPropertyRollouts: INestedState = {
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

  const appFastPropertyDetails: INestedState = {
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
      fastProperty: ['$stateParams', ($stateParams: angular.ui.IStateParamsService) => {
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


  const mainProperty: INestedState = {
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

  const propInsights: INestedState = {
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

  const globalFastPropertyDetails: INestedState = {
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
      fastProperty: ['$stateParams', ($stateParams: IPropertyDetailsStateParams) => {
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


  const fastProperties: INestedState = {
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

  const data: INestedState = {
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

  applicationStateProvider.addChildState(fastPropertyRollouts);
  applicationStateProvider.addChildState(propInsights);
  stateConfigProvider.addToRootState(data);
});
