import { module } from 'angular';

import { APPLICATION_STATE_PROVIDER, ApplicationStateProvider } from 'core/application/application.state.provider';
import { SETTINGS } from 'core/config';
import { INestedState } from 'core/navigation/state.provider';

import { Environments } from './Environments';
import { getIsNewUI, uiFeatureFlag } from './Environments2';
import { Configuration } from './config/Configuration';
import { EnvironmentsOverview } from './overview/EnvironmentsOverview';
import { VersionsHistory } from './versionsHistory/VersionsHistory';

export type Routes = 'overview' | 'config' | 'history';

const routes: Array<INestedState & { name: Routes }> = [
  {
    name: 'overview',
    url: '/overview',
    component: EnvironmentsOverview,
    $type: 'react',
    children: [],
    data: {
      pageTitleSection: {
        title: 'Environments overview',
      },
    },
  },
  {
    name: 'config',
    url: '/config',
    component: Configuration,
    $type: 'react',
    children: [],
    data: {
      pageTitleSection: {
        title: 'Environments config',
      },
    },
  },
  {
    name: 'history',
    url: '/history/{version:.*}?sha',
    component: VersionsHistory,
    $type: 'react',
    children: [],
    params: {
      version: { isOptional: true, value: null },
      sha: { isOptional: true, value: null },
    },
    data: {
      pageTitleSection: {
        title: 'Environments history',
      },
    },
  },
];

export const MANAGED_STATES = 'spinnaker.core.managed.states';
module(MANAGED_STATES, [APPLICATION_STATE_PROVIDER]).config([
  'applicationStateProvider',
  (applicationStateProvider: ApplicationStateProvider) => {
    if (SETTINGS.feature.managedDelivery) {
      const isNewUI = () => {
        const isNew = getIsNewUI();
        if (isNew) {
          localStorage.setItem(uiFeatureFlag.key, uiFeatureFlag.value);
        } else {
          localStorage.removeItem(uiFeatureFlag.key);
        }
        return isNew;
      };

      const artifactVersion: INestedState = {
        name: 'artifactVersion',
        url: `/{reference}/{version}`,
        params: {
          reference: { dynamic: true },
          version: { dynamic: true },
        },
        children: [],
        redirectTo: (transition) => {
          if (isNewUI()) {
            return transition.targetState().withState('home.applications.application.environments.history');
          }
          return undefined;
        },
      };

      const environments: INestedState = {
        name: 'environments',
        url: `/environments`,
        views: {
          insight: { component: Environments, $type: 'react' },
        },
        data: {
          pageTitleSection: {
            title: 'Environments',
          },
        },
        children: [artifactVersion, ...routes],
        redirectTo: (transition) => {
          if (isNewUI()) {
            return transition.targetState().withState('home.applications.application.environments.overview');
          }
          return undefined;
        },
      };

      applicationStateProvider.addChildState(environments);
    }
  },
]);
