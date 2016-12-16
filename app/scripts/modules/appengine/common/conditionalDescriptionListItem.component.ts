import {module, IComponentOptions} from 'angular';

class ConditionalDescriptionListItem implements IComponentOptions {
  public bindings: any = {label: '@', key: '@', component: '<'};
  public transclude: boolean = true;
  public template: string = `
    <dt ng-if="$ctrl.component[$ctrl.key]">{{$ctrl.label}}<ng-transclude></ng-transclude></dt>
    <dd>{{$ctrl.component[$ctrl.key]}}</dd>
  `;
}

export const CONDITIONAL_DESCRIPTION_LIST_ITEM = 'spinnaker.appengine.conditionalDescriptionListItem';

module(CONDITIONAL_DESCRIPTION_LIST_ITEM, [])
  .component('conditionalDtDd', new ConditionalDescriptionListItem());
