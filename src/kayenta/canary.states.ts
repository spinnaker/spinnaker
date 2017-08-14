import { module } from 'angular';
import { UIRouter } from '@uirouter/angularjs';

import {
  INestedState,
  APPLICATION_STATE_PROVIDER,
  ApplicationStateProvider
} from '@spinnaker/core';

import ConfigDetailLoader from './edit/configDetailLoader';
import CanaryConfigSave from './edit/save';
import Canary from './canary';
import SelectConfig from './selectConfig';

export const CANARY_STATES = 'spinnaker.kayenta.canary.states';
module(CANARY_STATES, [APPLICATION_STATE_PROVIDER])
  .config((applicationStateProvider: ApplicationStateProvider) => {
  const configDetail: INestedState = {
    name: 'configDetail',
    url: '/canary/:configName',
    views: {
      detail: {
        component: ConfigDetailLoader, $type: 'react'
      },
      footer: {
        component: CanaryConfigSave, $type: 'react',
      }
    },
    params: {
      configName: { squash: true, value: null },
    },
    resolve: [
      {
        token: 'configNameStream',
        deps: [UIRouter],
        resolveFn: (uiRouter: any) => uiRouter.globals.params$,
      }
    ]
  };

  const canaryDefault: INestedState = {
    name: 'default',
    url: '/canary',
    views: {
      detail: {
        component: SelectConfig, $type: 'react'
      }
    }
  };

  const canary: INestedState = {
    abstract: true,
    name: 'canary',
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
    children: [canaryDefault, configDetail]
  };

  applicationStateProvider.addChildState(canary);
});
