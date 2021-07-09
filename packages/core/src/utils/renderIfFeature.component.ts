import { IComponentOptions, module } from 'angular';

import { SETTINGS } from '../config/settings';

class RenderIfFeatureController implements ng.IController {
  public feature: string;
  public featureEnabled = false;

  public $onInit(): void {
    this.featureEnabled = this.feature && SETTINGS.feature[this.feature];
  }
}

const renderIfFeatureComponent: IComponentOptions = {
  bindings: { feature: '@' },
  controller: RenderIfFeatureController,
  transclude: true,
  template: `<ng-transclude ng-if="$ctrl.featureEnabled"></ng-transclude>`,
};

export const RENDER_IF_FEATURE = 'spinnaker.core.renderIfFeature.directive';
module(RENDER_IF_FEATURE, []).component('renderIfFeature', renderIfFeatureComponent);
