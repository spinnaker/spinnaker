import { IController, module } from 'angular';
import { PageNavigationState } from './PageNavigationState';
import { isFunction, throttle } from 'lodash';
import { ScrollToService } from 'core/utils/scrollTo/scrollTo.service';
import { PAGE_SECTION_COMPONENT } from './pageSection.component';
import { UUIDGenerator } from 'core/utils/uuid.service';
import './pageNavigation.less';

class PageNavigatorController implements IController {
  public scrollableContainer: string;
  public pageNavigationState: PageNavigationState = PageNavigationState;
  private container: JQuery;
  private navigator: JQuery;
  private id: string;

  private getEventKey(): string {
    return `scroll.pageNavigation.${this.id}`;
  }

  public constructor(private $element: JQuery) {
    'ngInject';
  }

  public $onInit(): void {
    this.id = UUIDGenerator.generateUuid();
    PageNavigationState.reset();
    this.container = this.$element.closest(this.scrollableContainer);
    if (isFunction(this.container.bind)) {
      this.container.bind(this.getEventKey(), throttle(() => this.handleScroll(), 20));
    }
    this.navigator = this.$element.find('.page-navigation');
  }

  public $onDestroy(): void {
    if (isFunction(this.container.unbind)) {
      this.container.unbind(this.getEventKey());
    }
  }

  public setCurrentSection(key: string): void {
    PageNavigationState.setCurrentPage(key);
    ScrollToService.scrollTo(`[data-page-id=${key}]`, this.scrollableContainer, this.container.offset().top);
    this.container.find('.highlighted').removeClass('highlighted');
    this.container.find(`[data-page-id=${key}]`).addClass('highlighted');
  }

  private handleScroll(): void {
    const navigatorRect = this.$element.get(0).getBoundingClientRect(),
      scrollableContainerTop = this.container.get(0).getBoundingClientRect().top;

    const currentPage = PageNavigationState.pages.find(p => {
      const content = this.container.find(`[data-page-content=${p.key}]`);
      if (content.length) {
        return content.get(0).getBoundingClientRect().bottom > scrollableContainerTop;
      }
      return false;
    });
    if (currentPage) {
      PageNavigationState.setCurrentPage(currentPage.key);
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
}

class PageNavigatorComponent implements ng.IComponentOptions {
  public bindings: any = {
    scrollableContainer: '@',
  };
  public controller: any = PageNavigatorController;
  public transclude = true;
  public template = `
    <div class="row">
      <div class="col-md-3 hidden-sm hidden-xs">
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
      <div class="col-md-9 col-sm-12">
        <div class="sections" ng-transclude></div>
      </div>
    </div>
  `;
}

export const PAGE_NAVIGATOR_COMPONENT = 'spinnaker.core.presentation.navigation.pageNavigator';

module(PAGE_NAVIGATOR_COMPONENT, [PAGE_SECTION_COMPONENT]).component('pageNavigator', new PageNavigatorComponent());
