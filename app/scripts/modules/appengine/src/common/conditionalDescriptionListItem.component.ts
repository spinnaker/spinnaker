import { module, IComponentOptions, IFilterService, IController } from 'angular';

class AppengineConditionalDescriptionListItemCtrl implements IController {
  public label: string;
  public key: string;
  public component: any;

  public static $inject = ['$filter'];
  constructor(private $filter: IFilterService) {}

  public $onInit(): void {
    if (!this.label) {
      this.label = this.$filter<Function>('robotToHuman')(this.key);
    }
  }
}

class AppengineConditionalDescriptionListItem implements IComponentOptions {
  public bindings: any = { label: '@', key: '@', component: '<' };
  public transclude: any = {
    keyLabel: '?keyText',
    valueLabel: '?valueLabel',
  };
  public template = `
    <dt ng-if="$ctrl.component[$ctrl.key]">{{$ctrl.label}}<span ng-transclude="keyLabel"></span></dt>
    <dd ng-if="$ctrl.component[$ctrl.key]">{{$ctrl.component[$ctrl.key]}}<span ng-transclude="valueLabel"></span></dd>
  `;
  public controller: any = AppengineConditionalDescriptionListItemCtrl;
}

export const APPENGINE_CONDITIONAL_DESCRIPTION_LIST_ITEM = 'spinnaker.appengine.conditionalDescriptionListItem';

module(APPENGINE_CONDITIONAL_DESCRIPTION_LIST_ITEM, []).component(
  'appengineConditionalDtDd',
  new AppengineConditionalDescriptionListItem(),
);
