import { module } from 'angular';

import { INestedState } from 'core/navigation/state.provider';
import { APPLICATION_STATE_PROVIDER, ApplicationStateProvider } from 'core/application/application.state.provider';
import { SETTINGS } from 'core/config';
import { Environments } from './Environments';

export const MANAGED_STATES = 'spinnaker.core.managed.states';
module(MANAGED_STATES, [APPLICATION_STATE_PROVIDER]).config([
  'applicationStateProvider',
  (applicationStateProvider: ApplicationStateProvider) => {
    if (SETTINGS.feature.managedDelivery) {
      const artifactVersion: INestedState = {
        name: 'artifactVersion',
        url: '/{type}/{name}/{version}',
        params: {
          type: { dynamic: true },
          name: { dynamic: true },
          version: { dynamic: true },
        },
        children: [],
      };

      const environments: INestedState = {
        name: 'environments',
        url: '/environments',
        views: {
          insight: { component: Environments, $type: 'react' },
        },
        data: {
          pageTitleSection: {
            title: 'Environments',
          },
        },
        children: [artifactVersion],
      };

      applicationStateProvider.addChildState(environments);
    }
  },
]);
