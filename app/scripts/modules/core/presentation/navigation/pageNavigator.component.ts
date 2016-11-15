import {module} from 'angular';
import {PageNavigationState, PAGE_NAVIGATION_STATE} from './pageNavigationState';
import {throttle} from 'lodash';
import {ScrollToService, SCROLL_TO_SERVICE} from '../../utils/scrollTo/scrollTo.service';
import {PAGE_SECTION_COMPONENT} from './pageSection.component';
import './pageNavigation.less';

class PageNavigatorController implements ng.IComponentController {
  public pageNavigationState: PageNavigationState;
  public scrollableContainer: string;
  private container: JQuery;
  private navigator: JQuery;

  private static get EVENT_KEY(): string { return 'scroll.pageNavigation'; }

  static get $inject() { return ['$element', 'scrollToService', 'pageNavigationState']; }

  public constructor(private $element: JQuery, private scrollToService: ScrollToService, public pageNavigationState: PageNavigationState) {}

  public $onInit(): void {
    this.pageNavigationState.reset();
    this.container = this.$element.closest(this.scrollableContainer);
    this.container.bind(PageNavigatorController.EVENT_KEY, throttle(() => this.handleScroll(), 20));
    this.navigator = this.$element.find('.page-navigation');
  }

  public $onDestroy(): void {
    this.container.unbind(PageNavigatorController.EVENT_KEY);
  }

  public setCurrentSection(key: string): void {
    this.pageNavigationState.setCurrentPage(key);
    this.scrollToService.scrollTo(`[data-page-id=${key}]`, this.scrollableContainer, this.container.offset().top);
    this.container.find('.highlighted').removeClass('highlighted');
    this.container.find(`[data-page-id=${key}]`).addClass('highlighted');
  }

  private handleScroll(): void {
    const navigatorRect = this.$element.get(0).getBoundingClientRect(),
          scrollableContainerTop = this.container.get(0).getBoundingClientRect().top;

    const currentPage = this.pageNavigationState.pages.find(p => {
      const content = this.container.find(`[data-page-content=${p.key}]`);
      if (content.length) {
        return content.get(0).getBoundingClientRect().bottom > scrollableContainerTop;
      }
      return false;
    });
    if (currentPage) {
      this.pageNavigationState.setCurrentPage(currentPage.key);
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
  public controller: ng.IComponentController = PageNavigatorController;
  public transclude: boolean = true;
  public template: string = `
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

module(PAGE_NAVIGATOR_COMPONENT, [
  PAGE_NAVIGATION_STATE,
  SCROLL_TO_SERVICE,
  PAGE_SECTION_COMPONENT
]).component('pageNavigator', new PageNavigatorComponent());
