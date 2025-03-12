import { Transition, UIRouter } from '@uirouter/core';
import * as Creators from 'kayenta/actions/creators';
import Canary, { canaryStore } from 'kayenta/canary';
import ConfigDetailLoader from 'kayenta/edit/configDetailLoader';
import CanaryConfigEdit from 'kayenta/edit/edit';
import CanaryConfigSave from 'kayenta/edit/save';
import SelectConfig from 'kayenta/edit/selectConfig';
import ResultDetailLoader from 'kayenta/report/detail/detailLoader';
import ExecutionListLoadStates from 'kayenta/report/list/loadStates';
import Report from 'kayenta/report/report';
import { $rootScope } from 'ngimport';

import { ApplicationStateProvider, INestedState } from '@spinnaker/core';

import '../canary.dataSource.bridge';
import { CanarySettings } from '../canary.settings';

export function registerStates($uiRouter: UIRouter, applicationStateProvider: ApplicationStateProvider) {
  const configDetail: INestedState = {
    name: 'configDetail',
    url: '/:id?copy&new',
    views: {
      detail: {
        component: ConfigDetailLoader,
        $type: 'react',
      },
      footer: {
        component: CanaryConfigSave,
        $type: 'react',
      },
    },
    params: {
      copy: { type: 'boolean', value: false, squash: true },
      new: { type: 'boolean', value: false, squash: true },
    },
    resolve: [
      {
        token: 'configNameStream',
        deps: [UIRouter],
        resolveFn: (uiRouter: any) => uiRouter.globals.params$,
      },
    ],
  };

  const configDefault: INestedState = {
    name: 'configDefault',
    url: '',
    views: {
      detail: {
        component: SelectConfig,
        $type: 'react',
      },
    },
  };

  const config: INestedState = {
    name: 'canaryConfig',
    url: '/config',
    views: {
      canary: {
        component: CanaryConfigEdit,
        $type: 'react',
      },
    },
    children: [configDefault, configDetail],
    redirectTo: (transition) => transition.to().name + '.configDefault',
  };

  const reportDetail: INestedState = {
    name: 'reportDetail',
    url: '/:configId/:runId',
    views: {
      detail: {
        component: ResultDetailLoader,
        $type: 'react',
      },
    },
    resolve: [
      {
        token: 'resultIdStream',
        deps: [UIRouter],
        resolveFn: (uiRouter: any) => uiRouter.globals.params$,
      },
    ],
  };

  const reportDefault: INestedState = {
    name: 'reportDefault',
    url: '',
    views: {
      detail: {
        component: ExecutionListLoadStates,
        $type: 'react',
      },
    },
  };

  const report: INestedState = {
    name: 'report',
    url: '/report?count',
    views: {
      canary: {
        component: Report,
        $type: 'react',
      },
    },
    params: {
      count: {
        type: 'int',
        value: CanarySettings.defaultExecutionCount ?? CanarySettings.executionsCountOptions?.[0] ?? 20,
        squash: true,
      },
    },
    children: [reportDetail, reportDefault],
    redirectTo: (transition) => transition.to().name + '.reportDefault',
  };

  const canaryRoot: INestedState = {
    abstract: true,
    name: 'canary',
    url: '/canary',
    views: {
      insight: {
        component: Canary,
        $type: 'react',
      },
    },
    data: {
      pageTitleSection: {
        title: 'Canary',
      },
    },
    children: [config, report],
  };

  applicationStateProvider.addChildState(canaryRoot);
}

export function registerTransitionHooks($uiRouter: UIRouter) {
  // When leaving a config detail state, clear that config.
  $uiRouter.transitionService.onBefore(
    {
      from: '**.configDetail.**',
      to: (state) => !state.name.includes('configDetail'),
    },
    () => {
      canaryStore.dispatch(Creators.clearSelectedConfig());
    },
  );

  // Prompts confirmation for page navigation if config hasn't been saved.
  // Should be possible with a $uiRouter transition hook, but it's not.
  $rootScope.$on('$stateChangeStart', (event) => {
    const state = canaryStore.getState();
    const warningMessage = 'You have unsaved changes.\nAre you sure you want to navigate away from this page?';
    if (state.selectedConfig && !state.selectedConfig.isInSyncWithServer) {
      if (!window.confirm(warningMessage)) {
        event.preventDefault();
      }
    }
  });

  // Prompts confirmation for page reload if config hasn't been saved.
  $uiRouter.transitionService.onEnter({ to: '**.configDetail.**' }, () => {
    window.onbeforeunload = () => {
      const state = canaryStore.getState();
      // Must return `null` if reload should be allowed.
      return (state.selectedConfig && !state.selectedConfig.isInSyncWithServer) || null;
    };
  });

  // Clears reload hook when leaving canary config view.
  $uiRouter.transitionService.onExit({ from: '**.configDetail.**' }, () => {
    window.onbeforeunload = undefined;
  });

  $uiRouter.transitionService.onSuccess({ to: '**.report.**' }, (transition: Transition) => {
    if (transition.params('from').count !== transition.params('to').count) {
      canaryStore.dispatch(Creators.setExecutionsCount({ count: transition.params('to').count }));
    }
  });
}
