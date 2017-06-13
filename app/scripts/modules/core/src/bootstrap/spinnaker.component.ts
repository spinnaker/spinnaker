import { bootstrapModule } from './bootstrap.module';
import { OverrideRegistry } from 'core/overrideRegistry';
import { IFeatures, SETTINGS } from '@spinnaker/core';

const template = `
  <div class="spinnaker-container">
    <div class="transition-overlay" ng-if="!authenticating && routing">
      <h1 us-spinner="{radius:30, width:8, length: 16}"></h1>
    </div>
    <div class="spinnaker-header navbar navbar-inverse">
      <div ng-include="$ctrl.spinnakerHeaderTemplate"></div>
    </div>

    <div class="spinnaker-content">
      <div ui-view="main" ng-if="!authenticating"></div>
    </div>
  </div>
  <notifier></notifier>
`;

class SpinnakerController {
  public spinnakerHeaderTemplate: string;
  public feature: IFeatures;
  constructor (overrideRegistry: OverrideRegistry) {
    'ngInject';
    this.spinnakerHeaderTemplate = overrideRegistry.getTemplate('spinnakerHeader', require('./spinnakerHeader.html'));
    this.feature = SETTINGS.feature;
  }
}

bootstrapModule.component('spinnaker', {
  template,
  controller: SpinnakerController,
});
