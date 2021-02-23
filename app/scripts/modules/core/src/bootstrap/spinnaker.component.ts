import { IController } from 'angular';
import { IFeatures, SETTINGS } from 'core/config/settings';
import { IDeckRootScope } from 'core/domain';

import { bootstrapModule } from './bootstrap.module';

const template = `
  <spinnaker-container authenticating="$ctrl.authenticating" routing="$ctrl.routing"></spinnaker-container>
`;

class SpinnakerController implements IController {
  public authenticating: boolean;
  public feature: IFeatures;
  public routing: boolean;
  public static $inject = ['$rootScope'];
  constructor($rootScope: IDeckRootScope) {
    this.feature = SETTINGS.feature;
    this.authenticating = $rootScope.authenticating;
    this.routing = $rootScope.routing;
  }
}

bootstrapModule.component('spinnaker', {
  template,
  controller: SpinnakerController,
});
