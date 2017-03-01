import {module} from 'angular';

class RenderIfFeatureController implements ng.IComponentController {
  public feature: string;
  public featureEnabled = false;

  constructor(private settings: any) {}

  public $onInit(): void {
    this.featureEnabled = this.feature && this.settings.feature[this.feature];
  }
}

class RenderIfFeatureComponent implements ng.IComponentOptions {
  public bindings: any = {feature: '@'};
  public controller: any = RenderIfFeatureController;
  public transclude = true;
  public template = `<ng-transclude ng-if="$ctrl.featureEnabled"></ng-transclude>`;
}

export const RENDER_IF_FEATURE = 'spinnaker.core.renderIfFeature.directive';
module(RENDER_IF_FEATURE, [require('core/config/settings.js')])
  .component('renderIfFeature', new RenderIfFeatureComponent());
