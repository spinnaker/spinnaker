import { module } from 'angular';
import { INestedState, STATE_CONFIG_PROVIDER, StateConfigProvider } from '../navigation/state.provider';

export const STYLEGUIDE_STATES = 'spinnaker.core.styleguide.states';

module(STYLEGUIDE_STATES, [STATE_CONFIG_PROVIDER]).config([
  'stateConfigProvider',
  (stateConfigProvider: StateConfigProvider) => {
    const styleguideState: INestedState = {
      url: '/styleguide',
      name: 'styleguide',
      views: {
        'main@': {
          templateUrl: '/styleguide.html',
        },
      },
      data: {
        pageTitleSection: {
          title: 'Styleguide',
        },
      },
    };
    stateConfigProvider.addToRootState(styleguideState);
  },
]);
