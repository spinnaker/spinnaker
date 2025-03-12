import type { IComponentOptions, IController, IFilterService } from 'angular';
import { module } from 'angular';

class CloudrunConditionalDescriptionListItemCtrl implements IController {
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

const cloudrunConditionalDescriptionListItem: IComponentOptions = {
  bindings: { label: '@', key: '@', component: '<' },
  transclude: {
    keyLabel: '?keyText',
    valueLabel: '?valueLabel',
  },
  template: `
    <dt ng-if="$ctrl.component[$ctrl.key]">{{$ctrl.label}}<span ng-transclude="keyLabel"></span></dt>
    <dd ng-if="$ctrl.component[$ctrl.key]">{{$ctrl.component[$ctrl.key]}}<span ng-transclude="valueLabel"></span></dd>
  `,
  controller: CloudrunConditionalDescriptionListItemCtrl,
};

export const CLOUDRUN_CONDITIONAL_DESCRIPTION_LIST_ITEM = 'spinnaker.cloudrun.conditionalDescriptionListItem';

module(CLOUDRUN_CONDITIONAL_DESCRIPTION_LIST_ITEM, []).component(
  'cloudrunConditionalDtDd',
  cloudrunConditionalDescriptionListItem,
);
