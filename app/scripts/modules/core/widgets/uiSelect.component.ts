import {Directive, ElementRef, Injector, Input} from '@angular/core';
import {UpgradeComponent} from '@angular/upgrade/static';
import {module, IComponentOptions} from 'angular';

class UiSelectComponent implements IComponentOptions {

  public bindings: any = {
    items: '<',
    model: '<',
    modelProperty: '@',
    placeholder: '@',
    renderProperty: '@',
    selectProperty: '@'
  };
  public template = `
    <ui-select ng-model="$ctrl.model[$ctrl.modelProperty]" class="form-control input-sm" required>
      <ui-select-match placeholder="{{$ctrl.placeholder}}">{{$select.selected[$ctrl.renderProperty]}}</ui-select-match>
      <ui-select-choices repeat="item[$ctrl.selectProperty] as item in $ctrl.items | filter: $select.search">
        {{item[$ctrl.renderProperty]}}
      </ui-select-choices>
    </ui-select>
  `;
}

const selector = 'uiSelectWrapper';
export const UI_SELECT_COMPONENT = 'spinnaker.core.widget.uiSelect.component';
module(UI_SELECT_COMPONENT, []).component(selector, new UiSelectComponent());

@Directive({
  selector: 'ui-select-wrapper'
})
export class UiSelectComponentDirective extends UpgradeComponent {

  @Input()
  public items: any[];

  @Input()
  public model: any;

  @Input()
  public modelProperty: string;

  @Input()
  public placeholder: string;

  @Input()
  public renderProperty: string;

  @Input()
  public selectProperty: string;

  constructor(elementRef: ElementRef, injector: Injector) {
    super(selector, elementRef, injector);
  }
}
