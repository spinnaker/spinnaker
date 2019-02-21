import { IController } from 'angular';
import { react2angular } from 'react2angular';

import { bootstrapModule } from './bootstrap.module';
import { OverrideRegistry } from 'core/overrideRegistry';
import { IFeatures, SETTINGS } from 'core/config/settings';

import { SpinnakerHeader } from 'core/header/SpinnakerHeader';

const template = `
  <div class="spinnaker-container grid-container">
    <div class="transition-overlay" ng-if="!authenticating && routing">
      <loading-spinner size="'medium'"></loading-spinner>
    </div>
    <div class="navbar-inverse grid-header">
      <custom-banner></custom-banner>
      <div ng-include="$ctrl.spinnakerHeaderTemplate"></div>
    </div>

    <div class="spinnaker-content grid-contents">
      <div ui-view="main" ng-if="!authenticating"></div>
    </div>
  </div>
  <notifier></notifier>
`;

class SpinnakerController implements IController {
  public spinnakerHeaderTemplate: string;
  public feature: IFeatures;
  public static $inject = ['overrideRegistry'];
  constructor(overrideRegistry: OverrideRegistry) {
    react2angular(SpinnakerHeader);
    this.spinnakerHeaderTemplate = overrideRegistry.getTemplate('spinnakerHeader', require('./spinnakerHeader.html'));
    this.feature = SETTINGS.feature;
  }
}

bootstrapModule.component('spinnaker', {
  template,
  controller: SpinnakerController,
});
