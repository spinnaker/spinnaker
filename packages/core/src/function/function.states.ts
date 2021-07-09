import { StateParams } from '@uirouter/angularjs';
import { module } from 'angular';

import { FunctionDetails } from './FunctionDetails';
import { Functions } from './Functions';
import { APPLICATION_STATE_PROVIDER, ApplicationStateProvider } from '../application';
import { SETTINGS } from '../config/settings';
import { filterModelConfig } from './filter/FunctionFilterModel';
import { FunctionFilters } from './filter/FunctionFilters';
import { INestedState, StateConfigProvider } from '../navigation';
export const FUNCTION_STATES = 'spinnaker.core.functions.states';
module(FUNCTION_STATES, [APPLICATION_STATE_PROVIDER]).config([
  'applicationStateProvider',
  'stateConfigProvider',
  (applicationStateProvider: ApplicationStateProvider, stateConfigProvider: StateConfigProvider) => {
    if (!SETTINGS.feature.functions) {
      return;
    }
    const functionDetails: INestedState = {
      name: 'functionDetails',
      url: '/functionDetails/:cloudProvider/:account/:region/:functionName',
      views: {
        'detail@../insight': {
          component: FunctionDetails,
          $type: 'react',
        },
      },
      resolve: {
        accountId: ['$stateParams', ($stateParams: StateParams) => $stateParams.account],
        functionObj: [
          '$stateParams',
          ($stateParams: StateParams) => {
            return {
              functionName: $stateParams.functionName,
              account: $stateParams.account,
              region: $stateParams.region,
            };
          },
        ],
      },
      data: {
        pageTitleDetails: {
          title: 'Function Details',
          nameParam: 'functionName',
          accountParam: 'credentials',
          regionParam: 'region',
        },
        history: {
          type: 'functions',
        },
      },
    };

    const functions: INestedState = {
      url: `/functions?${stateConfigProvider.paramsToQuery(filterModelConfig)}`,
      name: 'functions',
      views: {
        nav: { component: FunctionFilters, $type: 'react' },
        master: { component: Functions, $type: 'react' },
      },
      params: stateConfigProvider.buildDynamicParams(filterModelConfig),
      data: {
        pageTitleSection: {
          title: 'Functions',
        },
      },
      children: [],
    };
    applicationStateProvider.addInsightState(functions);
    applicationStateProvider.addInsightDetailState(functionDetails);
  },
]);
