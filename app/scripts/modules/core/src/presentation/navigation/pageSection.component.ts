import { IChangesObject, IComponentOptions, IController, IOnChangesObject, module } from 'angular';

import { INavigationPage, PageNavigationState } from './PageNavigationState';

interface IPageSectionOnChanges extends IOnChangesObject {
  visible: IChangesObject<boolean>;
  label: IChangesObject<string>;
  badge: IChangesObject<string>;
}

class PageSectionController implements IController {
  public key: string;
  public label: string;
  public badge: string;
  public visible: boolean;
  public noWrapper: boolean;
  private pageConfig: INavigationPage;

  public $onInit(): void {
    this.visible = this.visible !== false;
    this.pageConfig = {
      key: this.key,
      label: this.label,
      visible: this.visible,
      badge: this.badge,
    };
    PageNavigationState.registerPage(this.pageConfig);
  }

  public $onChanges(changes: IPageSectionOnChanges): void {
    if (changes.visible && !changes.visible.isFirstChange()) {
      this.pageConfig.visible = changes.visible.currentValue;
    }
    if (changes.label && !changes.label.isFirstChange()) {
      this.pageConfig.label = changes.label.currentValue;
    }
    if (changes.badge && !changes.badge.isFirstChange()) {
      this.pageConfig.badge = changes.badge.currentValue;
    }
  }
}

const pageSectionComponent: IComponentOptions = {
  bindings: {
    key: '@',
    label: '@',
    badge: '<',
    visible: '<',
    noWrapper: '<',
  },
  controller: PageSectionController,
  transclude: true,
  template: `
    <div ng-if="$ctrl.pageConfig.visible" class="page-subheading flex-1" data-page-id="{{$ctrl.pageConfig.key}}">
      <h4 class="sticky-header">{{$ctrl.pageConfig.label}}</h4>
      <div ng-class="$ctrl.noWrapper ? 'no-wrapper' : 'section-body'" data-page-content="{{$ctrl.pageConfig.key}}" ng-transclude></div>
    </div>
  `,
};

export const PAGE_SECTION_COMPONENT = 'spinnaker.core.presentation.navigation.pageSection';

module(PAGE_SECTION_COMPONENT, []).component('pageSection', pageSectionComponent);
