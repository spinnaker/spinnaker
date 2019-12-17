import $ from 'jquery';
import { mock } from 'angular';

import { PAGE_NAVIGATOR_COMPONENT } from './pageNavigator.component';
import { INavigationPage } from './PageNavigationState';
import { ScrollToService } from '../../utils/scrollTo/scrollTo.service';
import UI_ROUTER from '@uirouter/angularjs';

describe('Component: Page Navigator', () => {
  let $compile: ng.ICompileService, $scope: ng.IScope, $timeout: ng.ITimeoutService, elem: JQuery;

  beforeEach(mock.module(PAGE_NAVIGATOR_COMPONENT, UI_ROUTER));

  beforeEach(
    mock.inject((_$compile_: ng.ICompileService, $rootScope: ng.IScope, _$timeout_: ng.ITimeoutService) => {
      $compile = _$compile_;
      $scope = $rootScope.$new();
      $timeout = _$timeout_;
    }),
  );

  const initialize = (pages: INavigationPage[]) => {
    $scope['pages'] = pages;

    let dom = `
      <div class="container" style="height: 60px; overflow-y: scroll">
        <page-navigator scrollable-container=".container">`;
    pages.forEach((_p, index) => {
      dom += `
          <page-section key="{{pages[${index}].key}}" label="{{pages[${index}].label}}" visible="pages[${index}].visible">
            <div style="height: 100px"></div>
          </page-section>`;
    });
    dom += `
        </page-navigator>
      </div>`;
    elem = $compile(dom)($scope);
    $scope.$digest();
  };

  describe('initialization', () => {
    it('renders all pages when no visible flag is set', () => {
      const pages = [
        { key: '1', label: 'Page 1' },
        { key: '2', label: 'Page 2' },
      ];
      initialize(pages);
      expect(elem.find('h4').length).toBe(2);
      expect(elem.find('h4:eq(0)')).textMatch('Page 1');
      expect(elem.find('h4:eq(1)')).textMatch('Page 2');
    });

    it('renders pages conditionally based on visible flag', () => {
      const pages = [
        { key: '1', label: 'Page 1', visible: true },
        { key: '2', label: 'Page 2', visible: false },
      ];
      initialize(pages);
      expect(elem.find('h4').length).toBe(1);
      expect(elem.find('h4:eq(0)')).textMatch('Page 1');

      pages[1].visible = true;
      $scope.$digest();
      expect(elem.find('h4').length).toBe(2);
    });
  });

  describe('navigation', () => {
    beforeEach(() => {
      $.fx.off = true;
      spyOn(ScrollToService, 'scrollTo');
    });
    it('scrolls to selected page and adds a highlighted class, removing from previously highlighted ones', () => {
      const pages = [
        { key: '1', label: 'Page 1' },
        { key: '2', label: 'Page 2' },
      ];
      initialize(pages);
      $scope.$digest();
      const navigator: JQuery = elem.find('.page-navigation');

      navigator.find('a:eq(1)').click();
      expect(elem.find('[data-page-id=2]').hasClass('highlighted')).toBe(true);
      $timeout.flush();
      expect(ScrollToService.scrollTo).toHaveBeenCalledWith('[data-page-id=2]', '.container', 0);

      navigator.find('a:eq(0)').click();
      expect(elem.find('[data-page-id=1]').hasClass('highlighted')).toBe(true);
      expect(elem.find('[data-page-id=2]').hasClass('highlighted')).toBe(false);
    });
  });
});
