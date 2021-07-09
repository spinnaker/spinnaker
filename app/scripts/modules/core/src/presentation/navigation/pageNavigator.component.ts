import { StateParams, StateService } from '@uirouter/core';
import { IController, module } from 'angular';
import { isFunction, throttle } from 'lodash';

import { PageNavigationState } from './PageNavigationState';
import { PAGE_SECTION_COMPONENT } from './pageSection.component';
import { ScrollToService } from '../../utils/scrollTo/scrollTo.service';
import { UUIDGenerator } from '../../utils/uuid.service';

import './pageNavigation.less';

class PageNavigatorController implements IController {
  public scrollableContainer: string;
  public pageNavigationState: PageNavigationState = PageNavigationState;
  private container: JQuery;
  private navigator: JQuery;
  private id: string;
  private deepLinkParam: string;
  public hideNavigation = false;

  private getEventKey(): string {
    return `scroll.pageNavigation.${this.id}`;
  }

  public static $inject = ['$element', '$state', '$stateParams'];
  public constructor(private $element: JQuery, private $state: StateService, private $stateParams: StateParams) {}

  public $onInit(): void {
    this.id = UUIDGenerator.generateUuid();
    PageNavigationState.reset();
    this.container = this.$element.closest(this.scrollableContainer);
    if (isFunction(this.container.bind) && !this.hideNavigation) {
      this.container.bind(
        this.getEventKey(),
        throttle(() => this.handleScroll(), 20),
      );
    }
    this.navigator = this.$element.find('.page-navigation');
    if (this.deepLinkParam && this.$stateParams[this.deepLinkParam]) {
      this.setCurrentSection(this.$stateParams[this.deepLinkParam]);
    }
  }

  public $onDestroy(): void {
    if (isFunction(this.container.unbind) && !this.hideNavigation) {
      this.container.unbind(this.getEventKey());
    }
  }

  public setCurrentSection(key: string): void {
    PageNavigationState.setCurrentPage(key);
    this.syncLocation(key);
    ScrollToService.scrollTo(`[data-page-id=${key}]`, this.scrollableContainer, this.container.offset().top);
    this.container.find('.highlighted').removeClass('highlighted');
    this.container.find(`[data-page-id=${key}]`).addClass('highlighted');
  }

  private handleScroll(): void {
    const navigatorRect = this.$element.get(0).getBoundingClientRect();
    const scrollableContainerTop = this.container.get(0).getBoundingClientRect().top;

    const currentPage = PageNavigationState.pages.find((p) => {
      const content = this.container.find(`[data-page-content=${p.key}]`);
      if (content.length) {
        return content.get(0).getBoundingClientRect().bottom > scrollableContainerTop;
      }
      return false;
    });
    if (currentPage) {
      PageNavigationState.setCurrentPage(currentPage.key);
      this.syncLocation(currentPage.key);
      this.navigator.find('li').removeClass('current');
      this.navigator.find(`[data-page-navigation-link=${currentPage.key}]`).addClass('current');
    }

    if (navigatorRect.top < scrollableContainerTop) {
      this.navigator.css({
        position: 'fixed',
        width: this.navigator.get(0).getBoundingClientRect().width,
        top: scrollableContainerTop,
      });
    } else {
      this.navigator.css({
        position: 'relative',
        top: 0,
        width: '100%',
      });
    }
  }

  private syncLocation(key: string) {
    if (this.deepLinkParam) {
      this.$state.go('.', { [this.deepLinkParam]: key }, { notify: false, location: 'replace' });
    }
  }
}

const pageNavigatorComponent: ng.IComponentOptions = {
  bindings: {
    scrollableContainer: '@',
    deepLinkParam: '@?',
    hideNavigation: '<?',
  },
  controller: PageNavigatorController,
  transclude: true,
  template: `
    <div class="row flex-1">
      <div class="col-md-3 hidden-sm hidden-xs flex-1" ng-show="!$ctrl.hideNavigation">
        <ul class="page-navigation">
          <li ng-repeat="page in $ctrl.pageNavigationState.pages"
              data-page-navigation-link="{{page.key}}"
              ng-if="page.visible"
              ng-class="{current: $ctrl.pageNavigationState.currentPageKey === page.key}">
            <a href ng-click="$ctrl.setCurrentSection(page.key)">
              {{page.label}}
              <span ng-if="page.badge">({{page.badge}})</span>
            </a>
          </li>
        </ul>
      </div>
      <div class="col-md-{{$ctrl.hideNavigation ? 12 : 9}} col-sm-12">
        <div class="sections" ng-transclude></div>
      </div>
    </div>
  `,
};

export const PAGE_NAVIGATOR_COMPONENT = 'spinnaker.core.presentation.navigation.pageNavigator';

module(PAGE_NAVIGATOR_COMPONENT, [PAGE_SECTION_COMPONENT]).component('pageNavigator', pageNavigatorComponent);
