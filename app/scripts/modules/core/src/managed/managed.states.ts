import { module } from 'angular';

import { APPLICATION_STATE_PROVIDER, ApplicationStateProvider } from 'core/application/application.state.provider';
import { SETTINGS } from 'core/config';
import { INestedState } from 'core/navigation/state.provider';

import { Environments } from './Environments';
import { featureFlag } from './Environments2';
import { Configuration } from './config/Configuration';
import { EnvironmentsOverview } from './overview/EnvironmentsOverview';

export type Routes = 'overview' | 'config';

const routes: Array<INestedState & { name: Routes }> = [
  {
    name: 'overview',
    url: '/overview',
    component: EnvironmentsOverview,
    $type: 'react',
    children: [],
  },
  {
    name: 'config',
    url: '/config',
    component: Configuration,
    $type: 'react',
    children: [],
  },
];

export const MANAGED_STATES = 'spinnaker.core.managed.states';
module(MANAGED_STATES, [APPLICATION_STATE_PROVIDER]).config([
  'applicationStateProvider',
  (applicationStateProvider: ApplicationStateProvider) => {
    if (SETTINGS.feature.managedDelivery) {
      const artifactVersion: INestedState = {
        name: 'artifactVersion',
        url: '/{reference}/{version}',
        params: {
          reference: { dynamic: true },
          version: { dynamic: true },
        },
        children: [],
      };

      const environments: INestedState = {
        name: 'environments',
        url: '/environments?{new_ui:query}',
        views: {
          insight: { component: Environments, $type: 'react' },
        },
        data: {
          pageTitleSection: {
            title: 'Environments',
          },
        },
        params: {
          new_ui: localStorage.getItem(featureFlag),
        },
        children: [artifactVersion, ...routes],
        redirectTo: (transition) => {
          const { new_ui } = transition.params();
          localStorage.setItem(featureFlag, '1');

          if (new_ui === '1') {
            return 'home.applications.application.environments.overview';
          }
          return undefined;
        },
      };

      applicationStateProvider.addChildState(environments);
    }
  },
]);
