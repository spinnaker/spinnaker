import { module } from 'angular';
import { UIRouter } from '@uirouter/angularjs';

import {
  INestedState,
  APPLICATION_STATE_PROVIDER,
  ApplicationStateProvider
} from '@spinnaker/core';
import ConfigDetailLoader from './edit/configDetailLoader';
import Canary from './canary';

export const CANARY_STATES = 'spinnaker.kayenta.canary.states';
module(CANARY_STATES, [APPLICATION_STATE_PROVIDER])
  .config((applicationStateProvider: ApplicationStateProvider) => {
  const config: INestedState = {
    name: 'configDetail',
    url: '/:configName',
    views: {
      detail: {
        component: ConfigDetailLoader, $type: 'react'
      }
    },
    resolve: [
      {
        token: 'configNameStream',
        deps: [UIRouter],
        resolveFn: (uiRouter: any) => uiRouter.globals.params$.map((param: any) => param.configName),
      }
    ]
  };

  const canary: INestedState = {
    name: 'canary',
    url: '/canary',
    views: {
      insight: {
        component: Canary, $type: 'react'
      },
    },
    data: {
      pageTitleSection: {
        title: 'Canary'
      }
    },
    children: [config]
  };

  applicationStateProvider.addChildState(canary);
});
