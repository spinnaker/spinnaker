import {module} from 'angular';
import {DirectiveFactory} from './tsDecorators/directiveFactoryDecorator';

class RenderIfFeatureDirectiveController {
  public $attrs: ng.IAttributes;
  public $element: JQuery;

  constructor(private settings: any) {}

  public initialize(): void {
    let feature: string = this.$attrs['renderIfFeature'];
    if (feature && !this.settings.feature[feature]) {
      this.$element.addClass('hidden');
    }
  }
}

@DirectiveFactory('settings')
class RenderIfFeatureDirective implements ng.IDirective {
  public restrict = 'A';
  public controller: any = RenderIfFeatureDirectiveController;
  public controllerAs = '$ctrl';

  public link($scope: ng.IScope, $element: JQuery, $attrs: ng.IAttributes) {
    let ctrl: RenderIfFeatureDirectiveController = $scope['$ctrl'];
    ctrl.$element = $element;
    ctrl.$attrs = $attrs;
    ctrl.initialize();
  }
}

export const RENDER_IF_FEATURE = 'spinnaker.core.renderIfFeature.directive';
module(RENDER_IF_FEATURE, [require('core/config/settings.js')])
  .directive('renderIfFeature', <any>RenderIfFeatureDirective);
