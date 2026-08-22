import { ApplicationConfig } from './ApplicationConfig';
import type { ApplicationStateProvider } from '../application.state.provider';
import { registerApplicationState } from '../applicationState.registration';
import type { INestedState } from '../../navigation';

registerApplicationState((applicationStateProvider: ApplicationStateProvider) => {
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
