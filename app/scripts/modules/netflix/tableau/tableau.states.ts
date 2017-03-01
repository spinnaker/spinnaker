import {module} from 'angular';

import {STATE_CONFIG_PROVIDER, INestedState, StateConfigProvider} from 'core/navigation/state.provider';
import {APPLICATION_STATE_PROVIDER, ApplicationStateProvider} from 'core/application/application.state.provider';

export const TABLEAU_STATES = 'spinnaker.netflix.tableau.states';
module(TABLEAU_STATES, [
  APPLICATION_STATE_PROVIDER,
  STATE_CONFIG_PROVIDER,
  require('./summary/summaryTableau.controller'),
  require('./application/appTableau.controller'),
  require('./tableau.dataSource'),
]).config((applicationStateProvider: ApplicationStateProvider, stateConfigProvider: StateConfigProvider) => {

  const appState: INestedState = {
    name: 'analytics',
    url: '/analytics',
    reloadOnSearch: false,
    views: {
      'insight': {
        templateUrl: require('./application/appTableau.html'),
        controller: 'AppTableauCtrl as ctrl',
      }
    },
    data: {
      pageTitleSection: {
        title: 'Analytics'
      }
    },
  };

  const summaryState: INestedState = {
    name: 'analytics',
    url: '/analytics',
    reloadOnSearch: false,
    views: {
      'main@': {
        templateUrl: require('./summary/summaryTableau.html'),
        controller: 'SummaryTableauCtrl as ctrl',
      }
    },
    data: {
      pageTitleSection: {
        title: 'Analytics'
      }
    },
  };

  applicationStateProvider.addChildState(appState);
  stateConfigProvider.addToRootState(summaryState);
});
