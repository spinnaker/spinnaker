import { module } from 'angular';

import {
  INestedState,
  APPLICATION_STATE_PROVIDER,
  ApplicationStateProvider
} from '@spinnaker/core';

export const CANARY_STATES = 'spinnaker.kayenta.canary.states';
module(CANARY_STATES, [APPLICATION_STATE_PROVIDER])
  .config((applicationStateProvider: ApplicationStateProvider) => {
  const tasks: INestedState = {
    name: 'canary',
    url: '/canary',
    views: {
      'insight': {
        template: '<canary application="ctrl.app"></canary>',
        controller: 'CanaryCtrl',
        controllerAs: 'ctrl'
      },
    },
    data: {
      pageTitleSection: {
        title: 'Canary'
      }
    },
  };

  applicationStateProvider.addChildState(tasks);
});
