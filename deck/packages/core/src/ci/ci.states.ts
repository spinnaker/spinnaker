import type { ApplicationStateProvider } from '../application';
import { registerApplicationState } from '../application';
import { Builds } from './components/Builds';
import { SETTINGS } from '../config/settings';
import type { INestedState } from '../navigation';

registerApplicationState((applicationStateProvider: ApplicationStateProvider) => {
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
});
