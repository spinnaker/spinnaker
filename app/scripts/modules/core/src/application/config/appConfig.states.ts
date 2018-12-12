import { module } from 'angular';

import { INestedState } from 'core/navigation';

import { ApplicationStateProvider, APPLICATION_STATE_PROVIDER } from '../application.state.provider';
import { ApplicationConfig } from './ApplicationConfig';

export const APP_CONFIG_STATES = 'spinnaker.core.application.states';
module(APP_CONFIG_STATES, [APPLICATION_STATE_PROVIDER]).config((applicationStateProvider: ApplicationStateProvider) => {
  const configState: INestedState = {
    name: 'config',
    url: '/config?section',
    views: {
      insight: {
        component: ApplicationConfig,
        $type: 'react',
      },
    },
    params: {
      section: {
        dynamic: true,
      },
    },
    data: {
      pageTitleSection: {
        title: 'Config',
      },
    },
  };

  applicationStateProvider.addChildState(configState);
});
