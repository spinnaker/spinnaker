import { module, IWindowService } from 'angular';
import { UIRouter } from '@uirouter/angularjs';

import {
  INestedState,
  APPLICATION_STATE_PROVIDER,
  ApplicationStateProvider,
  IDeckRootScope
} from '@spinnaker/core';

import ConfigDetailLoader from 'kayenta/edit/configDetailLoader';
import CanaryConfigEdit from 'kayenta/edit/edit';
import CanaryConfigSave from 'kayenta/edit/save';
import Canary, { canaryStore } from 'kayenta/canary';
import SelectConfig from 'kayenta/edit/selectConfig';
import Report from 'kayenta/report/report';
import ResultDetailLoader from 'kayenta/report/detailLoader';
import ResultList from 'kayenta/report/resultList';
import * as Creators from 'kayenta/actions/creators';

export const CANARY_STATES = 'spinnaker.kayenta.canary.states';
module(CANARY_STATES, [APPLICATION_STATE_PROVIDER])
  .config((applicationStateProvider: ApplicationStateProvider) => {
  const configDetail: INestedState = {
    name: 'configDetail',
    url: '/config/:id?copy&new',
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
    url: '/report/:configName/:runId',
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
}).run(($uiRouter: UIRouter, $window: IWindowService, $rootScope: IDeckRootScope) => {
  // When leaving a config detail state, clear that config.
  $uiRouter.transitionService.onBefore(
    {
      from: '**.configDetail.**',
      to: state => !state.name.includes('configDetail'),
    },
    () => { canaryStore.dispatch(Creators.clearSelectedConfig()); },
  );

  // Prompts confirmation for page navigation if config hasn't been saved.
  // Should be possible with a $uiRouter transition hook, but it's not.
  $rootScope.$on('$stateChangeStart', event => {
    const state = canaryStore.getState();
    const warningMessage = 'You have unsaved changes.\nAre you sure you want to navigate away from this page?';
    if (state.selectedConfig && !state.selectedConfig.isInSyncWithServer) {
      if (!$window.confirm(warningMessage)) {
        event.preventDefault();
      }
    }
  });

  // Prompts confirmation for page reload if config hasn't been saved.
  $uiRouter.transitionService.onEnter(
    { to: '**.configDetail.**' },
    () => {
      $window.onbeforeunload = () => {
        const state = canaryStore.getState();
        // Must return `null` if reload should be allowed.
        return (state.selectedConfig && !state.selectedConfig.isInSyncWithServer) || null;
      };
    }
  );

  // Clears reload hook when leaving canary config view.
  $uiRouter.transitionService.onExit(
    { from: '**.configDetail.**' },
    () => { $window.onbeforeunload = undefined; }
  );
});
