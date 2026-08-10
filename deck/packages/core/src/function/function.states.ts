import type { RawParams } from '@uirouter/core';

import { FunctionDetails } from './FunctionDetails';
import { Functions } from './Functions';
import type { ApplicationStateProvider } from '../application';
import { registerApplicationState } from '../application';
import { SETTINGS } from '../config/settings';
import { filterModelConfig } from './filter/FunctionFilterModel';
import { FunctionFilters } from './filter/FunctionFilters';
import type { INestedState, StateConfigProvider } from '../navigation';

registerApplicationState(
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
        accountId: ['$stateParams', ($stateParams: RawParams) => $stateParams.account],
        functionObj: [
          '$stateParams',
          ($stateParams: RawParams) => {
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
);
