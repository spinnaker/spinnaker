import {module, IAttributes, IDirective, ILogService, IWindowService, IScope} from 'angular';
import {DirectiveFactory} from 'core/utils/tsDecorators/directiveFactoryDecorator';
import * as $ from 'jquery';
import {throttle} from 'lodash';

require('./stickyHeader.less');

/**
 * Based on https://github.com/polarblau/stickySectionHeaders
 */

interface IStickyHeaderAttributes extends IAttributes {
  addedOffsetHeight: string;
  notifyOnly: string;
  stickyIf: string;
}

export class StickyHeaderController implements ng.IComponentController {
  public $element: JQuery;
  public $attrs: IStickyHeaderAttributes;
  public $scope: IScope;
  private $scrollableContainer: JQuery;
  private $section: JQuery;
  private id: number;
  private isSticky = false;
  private notifyOnly = false;
  private addedOffsetHeight = 0;
  private topOffset = 0;

  public constructor(private $log: ILogService, private $window: IWindowService) {}

  public initialize() {
    this.id = Math.round(Math.random() * Date.now());
    this.$section = this.$element.parent();
    this.$scrollableContainer = this.$element.closest('[sticky-headers]');
    this.isSticky = false;
    this.notifyOnly = this.$attrs.notifyOnly === 'true';
    this.positionHeader = throttle(this.positionHeader, 50);

    if (!this.$scrollableContainer.length) {
      this.$log.warn('No parent container with attribute "sticky-header"; headers will not stick.');
      return;
    }

    if (!this.notifyOnly) {
      this.$scrollableContainer.css({position: 'relative'});
    }

    this.addedOffsetHeight = this.$attrs.addedOffsetHeight ? parseInt(this.$attrs.addedOffsetHeight, 10) : 0;

    // fun thing about modals is they use a CSS transform, which resets position: fixed element placement -
    // but not offset() positioning, so we need to take that into account when calculating the fixed position
    const $modalDialog = this.$element.closest('div.modal-dialog');
    this.topOffset = $modalDialog.size() > 0 ? parseInt($modalDialog.css('marginTop'), 10) : 0;

    if (this.$attrs.stickyIf) {
      this.$scope.$watch(this.$attrs.stickyIf, (sticky: boolean) => this.toggleSticky(sticky));
    } else {
      this.toggleSticky(true);
    }
  }

  private positionHeader(): void {
    const sectionRect = this.$section.get(0).getBoundingClientRect(),
      sectionTop = sectionRect.top,
      windowHeight = this.$window.innerHeight,
      bottom = sectionRect.bottom;

    if (bottom < 0 || sectionTop > windowHeight) {
      this.clearStickiness();
      return;
    }

    const containerTop = this.$scrollableContainer.offset().top + this.addedOffsetHeight,
          top = sectionTop - containerTop;

    if (top < 0 && bottom > containerTop) {
      const headingRect = this.$element.get(0).getBoundingClientRect(),
            headingWidth = headingRect.width,
            headingHeight = this.$element.outerHeight(true);

      let topBase = containerTop,
          zIndex = 5;

      if (containerTop + headingHeight > bottom) {
        topBase = bottom - headingHeight;
        zIndex = 4;
      }

      const newHeaderStyle = {
        top: topBase - this.topOffset,
        width: headingWidth,
        zIndex: zIndex
      };
      if (this.notifyOnly) {
        this.$scope.$emit('sticky-header-enabled', newHeaderStyle);
      } else {
        this.$section.css({
          paddingTop: headingHeight,
        });
        this.$element.addClass('heading-sticky').removeClass('not-sticky').css(newHeaderStyle);
      }
      this.isSticky = true;
    } else {
      this.clearStickiness();
    }
  }

  private resetHeaderWidth(): void {
    if (this.$element.get(0).className.includes('heading-sticky')) {
      this.$element.removeClass('heading-sticky').addClass('not-sticky').removeAttr('style').css({width: '', top: ''});
    }
  }

  private handleWindowResize(): void {
    this.resetHeaderWidth();
    this.positionHeader();
  }

  private destroyStickyBindings(): void {
    this.$scrollableContainer.unbind('.stickyHeader-' + this.id);
    $(this.$window).unbind('.stickyHeader-' + this.id);
    this.$section.removeData();
    this.$element.removeData();
  }

  private clearStickiness(): void {
    if (this.isSticky) {
      if (this.notifyOnly) {
        this.$scope.$emit('sticky-header-disabled');
      } else {
        this.$section.css({
          paddingTop: 0,
        });
        this.resetHeaderWidth();
      }
    }
    this.isSticky = false;
  }

  private toggleSticky(enabled: boolean): void {
    if (enabled) {
      this.$scrollableContainer.bind(`scroll.stickyHeader-${this.id} resize.stickyHeader-${this.id}`, () => this.positionHeader());
      $(this.$window).bind('resize.stickyHeader-' + this.id, () => this.handleWindowResize());

      this.$scope.$on('page-reflow', () => this.handleWindowResize());

      this.$scope.$on('$destroy', () => this.destroyStickyBindings());
    } else {
      this.destroyStickyBindings();
    }
  }
}

@DirectiveFactory('$log', '$window')
class StickyHeaderDirective implements IDirective {
  public restrict = 'A';
  public controller: any = StickyHeaderController;

  public link = {
    post: this.postLink
  };

  private postLink($scope: IScope, $element: JQuery, $attrs: IStickyHeaderAttributes, ctrl: StickyHeaderController): void {
    ctrl.$scope = $scope;
    ctrl.$element = $element;
    ctrl.$attrs = $attrs;
    ctrl.initialize();
  }
}

export const STICKY_HEADER_DIRECTIVE = 'spinnaker.core.utils.stickyHeader';
module(STICKY_HEADER_DIRECTIVE, [])
  .directive('stickyHeader', <any>StickyHeaderDirective);
