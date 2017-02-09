import {module} from 'angular';
import { DirectiveFactory } from 'core/utils/tsDecorators/directiveFactoryDecorator';
import {Subject} from 'rxjs';

interface AutoScrollAttrs {
  autoScrollEnabled: string;
  autoScroll: string;
}

export class AutoScrollController implements ng.IComponentController {
  public autoScrollParent: string;
  public autoScrollEnabled: string;
  public onScroll: (event: Event) => void;
  public scrollToTop: Subject<boolean>;
  public $element: JQuery;
  public $attrs: AutoScrollAttrs;
  public $scope: ng.IScope;

  private scrollableContainer: JQuery;
  private scrollEnabled = true;

  private containerEvent: string = 'scroll.autoScrollWatcher';

  private toggleAutoScrollEnabled(newVal: boolean): void {
    if (newVal !== undefined) {
      this.scrollEnabled = newVal;
      this.autoScroll();
    }
  }

  private autoScroll(): void {
    if (this.scrollEnabled) {
      this.$timeout(() => this.scrollableContainer.scrollTop(this.$element.height()));
    }
  }

  public initialize(): void {
    this.scrollableContainer = this.autoScrollParent ? this.$element.closest(this.autoScrollParent) : this.$element.parent();
    if (this.onScroll) {
      this.scrollableContainer.on(this.containerEvent, (event: Event) => {
        this.$timeout(() => this.onScroll(event));
      });
    }
    if (this.scrollToTop) {
      this.scrollToTop.subscribe(() => this.$timeout(() => this.scrollableContainer.scrollTop(0)));
      this.$scope.$on('$destroy', () => this.scrollToTop.unsubscribe());
    }
    this.$scope.$watch(this.$attrs.autoScrollEnabled, (newVal: boolean) => this.toggleAutoScrollEnabled(newVal), true);
    this.$scope.$watch(this.$attrs.autoScroll, () => this.autoScroll(), true);
    this.$scope.$on('$destroy', () => this.scrollableContainer.off(this.containerEvent));
  }

  public constructor(private $timeout: ng.ITimeoutService) {}
}

@DirectiveFactory('$timeout')
class AutoScrollDirective implements ng.IDirective {
  restrict: string = 'A';
  controller: any = AutoScrollController;
  controllerAs: string = '$ctrl';
  bindToController: any = {
    autoScrollParent: '@?',
    autoScrollEnabled: '@?',
    onScroll: '=?',
    scrollToTop: '=?',
  };

  link($scope: ng.IScope, $element: JQuery, $attrs: AutoScrollAttrs, ctrl: AutoScrollController) {
    ctrl.$scope = $scope;
    ctrl.$element = $element;
    ctrl.$attrs = $attrs;
    ctrl.initialize();
  }
}


export const AUTO_SCROLL_DIRECTIVE = 'spinnaker.core.autoScroll';

module(AUTO_SCROLL_DIRECTIVE, [])
  .directive('autoScroll', <any>AutoScrollDirective);
