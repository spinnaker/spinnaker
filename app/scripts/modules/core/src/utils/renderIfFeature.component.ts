import { IComponentOptions, module } from 'angular';

import { SETTINGS } from '../config/settings';

class RenderIfFeatureController implements ng.IController {
  public feature: string;
  public featureEnabled = false;

  public $onInit(): void {
    this.featureEnabled = this.feature && SETTINGS.feature[this.feature];
  }
}

class RenderIfFeatureComponent implements IComponentOptions {
  public bindings: any = {feature: '@'};
  public controller: any = RenderIfFeatureController;
  public transclude = true;
  public template = `<ng-transclude ng-if="$ctrl.featureEnabled"></ng-transclude>`;
}

export const RENDER_IF_FEATURE = 'spinnaker.core.renderIfFeature.directive';
module(RENDER_IF_FEATURE, [])
  .component('renderIfFeature', new RenderIfFeatureComponent());
