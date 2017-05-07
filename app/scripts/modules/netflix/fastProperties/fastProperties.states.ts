import { IScope, module } from 'angular';
import { filterNames } from './view/filter/fastPropertyFilterSearch.component';

import {STATE_CONFIG_PROVIDER, INestedState, StateConfigProvider} from 'core/navigation/state.provider';
import {
  APPLICATION_STATE_PROVIDER, ApplicationStateProvider,
} from 'core/application/application.state.provider';
import { FAST_PROPERTY_ROLLOUTS_COMPONENT } from './view/rollouts/fastPropertyRollouts.component';
import { APPLICATION_PROPERTIES_COMPONENT } from './view/applicationProperties.component';
import { GLOBAL_PROPERTIES_COMPONENT } from './global/globalFastProperties.component';
import { GLOBAL_ROLLOUTS_COMPONENT } from './global/globalRollouts.component';
import { Application } from 'core/application/application.model';
import { ApplicationReader } from '../../core/application/service/application.read.service';

export const FAST_PROPERTY_STATES = 'spinnaker.netflix.fastProperties.states';
module(FAST_PROPERTY_STATES, [
  APPLICATION_STATE_PROVIDER,
  STATE_CONFIG_PROVIDER,
  FAST_PROPERTY_ROLLOUTS_COMPONENT,
  APPLICATION_PROPERTIES_COMPONENT,
  GLOBAL_PROPERTIES_COMPONENT,
  GLOBAL_ROLLOUTS_COMPONENT,
]).config((applicationStateProvider: ApplicationStateProvider, stateConfigProvider: StateConfigProvider) => {

  const filterParamsConfig = filterNames.map(f => { return { model: f, array: true }; });
  const filterParams = stateConfigProvider.buildDynamicParams(filterParamsConfig);
  filterParams.propertyId = {
    dynamic: true,
    type: 'string',
    value: null,
  };
  filterParams.sortBy = {
    dynamic: true,
    type: 'string',
    value: 'key',
  };
  filterParams.q = {
    dynamic: true,
    value: null,
  };

  const detailsView = {
    templateUrl: require('./view/details/fastPropertyDetails.html'),
    controller: 'FastPropertiesDetailsController',
    controllerAs: 'details'
  };

  // Shared by application and global views
  const executionDetails: INestedState = {
    name: 'execution',
    url: '/:executionId?refId&stage&step&details',
    params: {
      stage: {
        dynamic: true,
        type: 'int',
        value: 0,
      },
      step: {
        dynamic: true,
        type: 'int',
        value: 0,
      },
      details: {
        dynamic: true,
      }
    },
    data: {
      pageTitleDetails: {
        title: 'Execution Details',
        nameParam: 'executionId'
      }
    }
  };

  /*
    Application-specific views
   */
  const applicationFastProperties: INestedState = {
    name: 'properties',
    url: `/properties?propertyId&sortBy&${stateConfigProvider.paramsToQuery(filterParamsConfig)}`,
    views: {
      'master': {
        template: '<application-fast-properties application="app" class="flex-fill"></application-fast-properties>',
        controller: ($scope: IScope, app: Application) => { $scope.app = app; }
      },
      'detail': detailsView
    },
    params: filterParams,
    children: [
      executionDetails
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
      applicationFastProperties
    ]
  };

  /*
    Global Properties views
   */

  const globalFastPropertyRollouts: INestedState = {
    name: 'rollouts',
    url: `/rollouts?propertyId&${stateConfigProvider.paramsToQuery(filterParamsConfig)}`,
    views: {
      'master': {
        template: `<fast-property-rollouts></fast-property-rollouts>`,
      },
      'detail': detailsView,
    },
    data: {
      pageTitleSection: {
        title: 'Fast Property Rollouts'
      }
    },
    params: filterParams,
    children: [executionDetails]

  };

  const globalFastProperties: INestedState = {
    name: 'properties',
    url: `/properties?propertyId&q&sortBy&${stateConfigProvider.paramsToQuery(filterParamsConfig)}`,
    views: {
      'detail': detailsView,
    },
    params: filterParams,
  };

  const data: INestedState = {
    name: 'data',
    url: '/data',
    views: {
      'main@': {
        templateUrl: require('./global/main.html'),
        controller: ($scope: IScope, app: Application) => {
          $scope.app = app;
          app.global = true;
          app.enableAutoRefresh($scope);
        }
      }
    },
    data: {
      pageTitleMain: {
        label: 'Properties'
      }
    },
    resolve: {
      app: ['applicationReader', (applicationReader: ApplicationReader) => {
        return applicationReader.getApplication('spinnakerfp');
      }],
    },
    children: [
      globalFastProperties,
      globalFastPropertyRollouts,
    ]
  };

  applicationStateProvider.addChildState(propInsights);
  stateConfigProvider.addToRootState(data);
});
