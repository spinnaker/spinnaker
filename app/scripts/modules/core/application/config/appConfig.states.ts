import {module} from 'angular';
import {OverrideRegistry} from 'core/overrideRegistry/override.registry';
import {INestedState} from 'core/navigation/state.provider';
import {ApplicationStateProvider, APPLICATION_STATE_PROVIDER} from '../application.state.provider';

export const APP_CONFIG_STATES = 'spinnaker.core.application.states';
module(APP_CONFIG_STATES, [
  APPLICATION_STATE_PROVIDER
]).config((applicationStateProvider: ApplicationStateProvider) => {

  const configState: INestedState = {
    name: 'config',
    url: '/config',
    views: {
      'insight': {
        templateProvider: ['$templateCache', 'overrideRegistry',
          ($templateCache: ng.ITemplateCacheService, overrideRegistry: OverrideRegistry) => {
            let template: string = overrideRegistry.getTemplate('applicationConfigView', require('./applicationConfig.view.html'));
            return $templateCache.get(template);
        }],
        controller: 'ApplicationConfigController',
        controllerAs: 'config'
      },
    },
    data: {
      pageTitleSection: {
        title: 'Config'
      }
    }
  };

  applicationStateProvider.addChildState(configState);
});
