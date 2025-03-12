import { Subject } from 'rxjs';
import { AUTO_SCROLL_DIRECTIVE } from './autoScroll.directive';

describe('Directives: auto-scroll', function () {
  beforeEach(window.module(AUTO_SCROLL_DIRECTIVE));

  function buildContainer(height) {
    return `<div style="overflow:auto; height: ${height}px"></div>`;
  }
  function buildAutoScroller(watchers, attrs = '') {
    return `<div auto-scroll="${watchers}" ${attrs}></div>`;
  }
  function buildChild(height) {
    return `<div style="height: ${height}px"></div>`;
  }

  function buildWatchableContainer(watch, context, attrs = '') {
    var compile = context.compile,
      scope = context.scope;

    var container = angular.element(buildContainer(15)),
      autoScroller = angular.element(buildAutoScroller(watch, attrs)),
      child = angular.element(buildChild(50));

    autoScroller.append(child);
    container.append(autoScroller);
    container.appendTo('body');

    compile(container)(scope);
    scope.$digest();

    return container;
  }

  beforeEach(
    window.inject(function ($rootScope, $compile, _$timeout_) {
      this.scope = $rootScope.$new();
      this.compile = $compile;
      this.timeout = _$timeout_;
    }),
  );

  it('should scroll when a watched property is changed', function () {
    var scope = this.scope;
    scope.watched = 1;
    var container = buildWatchableContainer('watched', this);
    expect(container.scrollTop()).toBe(0);

    scope.watched = 2;
    scope.$digest();
    this.timeout.flush();

    expect(container.scrollTop()).toBe(35);
  });

  it('should scroll on deep changes', function () {
    var scope = this.scope;

    scope.watched = {
      field: 1,
    };
    var container = buildWatchableContainer('watched.field', this);

    scope.watched.field = 2;
    scope.$digest();
    this.timeout.flush();

    expect(container.scrollTop()).toBe(35);
  });

  it('should scroll on multiple changes', function () {
    var scope = this.scope;

    scope.watched = {
      field: 1,
    };
    scope.other = 'a';

    var container = buildWatchableContainer('[watched.field, other]', this);

    scope.watched.field = 2;
    scope.$digest();
    this.timeout.flush();

    expect(container.scrollTop()).toBe(35);

    container.scrollTop(0);

    scope.other = 'b';
    scope.$digest();
    this.timeout.flush();
    expect(container.scrollTop()).toBe(35);
  });

  it('should scroll after dom changes applied', function () {
    var compile = this.compile,
      scope = this.scope;

    scope.a = 0;
    scope.b = 0;

    var container = angular.element(buildContainer(25)),
      autoScroller = angular.element('<div auto-scroll="[a,b]"></div>'),
      childA = angular.element('<div ng-if="a === 1" style="height: 100px"></div>'),
      childB = angular.element('<div ng-if="b === 1" style="height: 60px"></div>');

    autoScroller.append(childA).append(childB);
    container.append(autoScroller);
    container.appendTo('body');

    compile(container)(scope);
    scope.$digest();
    expect(container.scrollTop()).toBe(0);

    scope.a = 1;
    scope.$digest();
    this.timeout.flush();
    expect(container.scrollTop()).toBe(75);

    scope.b = 1;
    scope.$digest();
    this.timeout.flush();
    expect(container.scrollTop()).toBe(135);

    scope.a = 2;
    scope.$digest();
    this.timeout.flush();
    expect(container.scrollTop()).toBe(35);

    container.height(170);
    scope.a = 1;
    scope.$digest();
    this.timeout.flush();
    expect(container.scrollTop()).toBe(0);
  });

  it('should not scroll when autoscroll is disabled', function () {
    const scope = this.scope;
    scope.isEnabled = true;
    scope.a = 0;

    const container = buildWatchableContainer('[a]', this, 'auto-scroll-enabled="isEnabled"');

    scope.a = 1;
    scope.$digest();
    this.timeout.flush();

    expect(container.scrollTop()).toBe(35);

    container.scrollTop(10);
    scope.isEnabled = false;
    scope.$digest();
    this.timeout.verifyNoPendingTasks();

    expect(container.scrollTop()).toBe(10);

    scope.isEnabled = true;
    scope.$digest();
    this.timeout.flush();

    expect(container.scrollTop()).toBe(35);
  });

  it('should scroll to top when triggered', function () {
    const scrollToTop = new Subject(),
      scope = this.scope;
    scope.scrollToTop = scrollToTop;
    scope.a = 0;

    const container = buildWatchableContainer('[a]', this, 'scroll-to-top="scrollToTop"');
    scope.a = 1;
    scope.$digest(0);
    this.timeout.flush();
    expect(container.scrollTop()).toBe(35);

    scrollToTop.next(true);

    this.timeout.flush();
    expect(container.scrollTop()).toBe(0);
  });
});
