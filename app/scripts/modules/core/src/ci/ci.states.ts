import { module } from 'angular';

import { APPLICATION_STATE_PROVIDER, ApplicationStateProvider } from '../application';
import { Builds } from './components/Builds';
import { SETTINGS } from '../config/settings';
import { INestedState } from '../navigation';

export const CI_STATES = 'spinnaker.ci.states';
export const name = CI_STATES;

module(CI_STATES, [APPLICATION_STATE_PROVIDER]).config([
  'applicationStateProvider',
  (applicationStateProvider: ApplicationStateProvider) => {
    if (!SETTINGS.feature.ci) {
      return;
    }
    const buildDetailTab: INestedState = {
      name: 'buildTab',
      url: '/:tab',
    };

    const buildDetail: INestedState = {
      name: 'build',
      url: '/:buildId',
      children: [buildDetailTab],
    };

    const builds: INestedState = {
      name: 'builds',
      url: '/builds',
      views: {
        insight: {
          component: Builds,
          $type: 'react',
        },
      },
      data: {
        pageTitleSection: {
          title: 'Builds',
        },
      },
      children: [buildDetail],
    };

    applicationStateProvider.addChildState(builds);
  },
]);
