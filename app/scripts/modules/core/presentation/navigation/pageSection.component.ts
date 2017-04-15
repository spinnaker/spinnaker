import {module} from 'angular';
import {PageNavigationState, INavigationPage} from './pageNavigationState';

interface IPageSectionOnChanges extends ng.IOnChangesObject {
  visible: ng.IChangesObject<boolean>;
  label: ng.IChangesObject<string>;
  badge: ng.IChangesObject<string>;
}

class PageSectionController implements ng.IComponentController {
  public key: string;
  public label: string;
  public badge: string;
  public visible: boolean;
  public noWrapper: boolean;
  private pageConfig: INavigationPage;

  public constructor(private pageNavigationState: PageNavigationState) {}

  public $onInit(): void {
    this.visible = this.visible !== false;
    this.pageConfig = {
      key: this.key,
      label: this.label,
      visible: this.visible,
      badge: this.badge,
    };
    this.pageNavigationState.registerPage(this.pageConfig);
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

class PageSectionComponent implements ng.IComponentOptions {
  public bindings: any = {
    key: '@',
    label: '@',
    badge: '<',
    visible: '<',
    noWrapper: '<',
  };
  public controller: any = PageSectionController;
  public transclude = true;
  public template = `
    <div ng-if="$ctrl.pageConfig.visible" class="page-subheading" data-page-id="{{$ctrl.pageConfig.key}}">
      <sticky-header>
        <h4>{{$ctrl.pageConfig.label}}</h4>
      </sticky-header>
      <div ng-class="$ctrl.noWrapper ? 'no-wrapper' : 'section-body'" data-page-content="{{$ctrl.pageConfig.key}}" ng-transclude></div>
    </div>
  `;
}

export const PAGE_SECTION_COMPONENT = 'spinnaker.core.presentation.navigation.pageSection';

module(PAGE_SECTION_COMPONENT, [])
  .component('pageSection', new PageSectionComponent());
