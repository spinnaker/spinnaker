import { module } from 'angular';
import { UIRouter } from '@uirouter/angularjs';

import {
  INestedState,
  APPLICATION_STATE_PROVIDER,
  ApplicationStateProvider
} from '@spinnaker/core';

import ConfigDetailLoader from 'kayenta/edit/configDetailLoader';
import CanaryConfigEdit from 'kayenta/edit/edit';
import CanaryConfigSave from 'kayenta/edit/save';
import Canary from 'kayenta/canary';
import SelectConfig from 'kayenta/selectConfig';
import Report from 'kayenta/report/report';
import ResultDetailLoader from 'kayenta/report/detailLoader';
import ResultList from 'kayenta/report/resultList';

export const CANARY_STATES = 'spinnaker.kayenta.canary.states';
module(CANARY_STATES, [APPLICATION_STATE_PROVIDER])
  .config((applicationStateProvider: ApplicationStateProvider) => {
  const configDetail: INestedState = {
    name: 'configDetail',
    url: '/config/:configName?copy&new',
    views: {
      detail: {
        component: ConfigDetailLoader, $type: 'react'
      },
      footer: {
        component: CanaryConfigSave, $type: 'react',
      }
    },
    params: {
      copy: { type: 'boolean', value: false, squash: true },
      'new': { type: 'boolean', value: false, squash: true },
    },
    resolve: [
      {
        token: 'configNameStream',
        deps: [UIRouter],
        resolveFn: (uiRouter: any) => uiRouter.globals.params$,
      }
    ]
  };

  const configDefault: INestedState = {
    name: 'configDefault',
    url: '/config',
    views: {
      detail: {
        component: SelectConfig, $type: 'react'
      }
    }
  };

  const config: INestedState = {
    name: 'canaryConfig',
    abstract: true,
    views: {
      canary: {
        component: CanaryConfigEdit, $type: 'react',
      }
    },
    children: [configDefault, configDetail],
  };

  const reportDetail: INestedState = {
    name: 'reportDetail',
    url: '/report/:id',
    views: {
      detail: {
        component: ResultDetailLoader, $type: 'react',
      },
    },
    resolve: [
      {
        token: 'resultIdStream',
        deps: [UIRouter],
        resolveFn: (uiRouter: any) => uiRouter.globals.params$,
      }
    ],
  };

  const reportDefault: INestedState = {
    name: 'reportDefault',
    url: '/report',
    views: {
      detail: {
        component: ResultList, $type: 'react',
      }
    },
  };

  const report: INestedState = {
    name: 'report',
    abstract: true,
    views: {
      canary: {
        component: Report, $type: 'react',
      },
    },
    children: [reportDetail, reportDefault],
  };

  const canaryRoot: INestedState = {
    abstract: true,
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
    children: [config, report],
  };

  applicationStateProvider.addChildState(canaryRoot);
});
