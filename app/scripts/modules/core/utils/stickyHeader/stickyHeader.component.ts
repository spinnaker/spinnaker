import { module, ILogService, IWindowService, IScope, IComponentOptions } from 'angular';
import * as $ from 'jquery';
import { throttle } from 'lodash';

import './stickyHeader.less';

/**
 * Based on https://github.com/polarblau/stickySectionHeaders
 */
export class StickyHeaderController implements ng.IComponentController {
  private addedOffsetHeight: number;
  private notifyOnly: boolean;
  private stickyIf: string;

  private $scrollableContainer: JQuery;
  private $section: JQuery;
  private $header: JQuery;
  private id: number;
  private isSticky = false;
  private topOffset = 0;

  static get $inject(): string[] { return ['$scope', '$element', '$log', '$window']; }
  public constructor(public $scope: IScope,
                     public $element: JQuery,
                     private $log: ILogService,
                     private $window: IWindowService) {}

  public $postLink(): void {
    this.id = Math.round(Math.random() * Date.now());
    this.$section = this.$element.parent(); // Maybe?
    this.$scrollableContainer = this.$element.closest('[sticky-headers]');
    this.isSticky = false;
    this.positionHeader = throttle(this.positionHeader, 50, {trailing: true});
    this.addedOffsetHeight = this.addedOffsetHeight || 0;

    if (!this.$scrollableContainer.length) {
      this.$log.warn('No parent container with attribute "sticky-headers"; headers will not stick.');
      return;
    }

    // fun thing about modals is they use a CSS transform, which resets position: fixed element placement -
    // but not offset() positioning, so we need to take that into account when calculating the fixed position
    const $modalDialog = this.$element.closest('div.modal-dialog');
    this.topOffset = $modalDialog.size() > 0 ? parseInt($modalDialog.css('marginTop'), 10) : 0;

    if (this.stickyIf !== undefined) {
      this.$scope.$watch(this.stickyIf, (sticky: boolean) => this.toggleSticky(sticky));
    } else {
      this.toggleSticky(true);
    }
  }

  // We need to wait for the first digest cycle to build the children, so this convenience function will
  // cache the header the first time we look it up
  private getHeader(): JQuery {
    if (!this.$header) {
      const header = this.$element.children().first();
      if (header.get(0) !== undefined) {
        this.$header = header;
      }
    }
    return this.$header;
  }

  private positionHeader(): void {
    const $header = this.getHeader(),
      sectionRect = this.$section.get(0).getBoundingClientRect(),
      sectionTop = sectionRect.top,
      windowHeight = this.$window.innerHeight,
      bottom = sectionRect.bottom;

    if (!$header || bottom < 0 || sectionTop > windowHeight) {
      this.clearStickiness();
      return;
    }

    const containerTop = this.$scrollableContainer.offset().top + this.addedOffsetHeight,
          top = sectionTop - containerTop;

    if (top < 0 && bottom > containerTop) {
      const headingRect = $header.get(0).getBoundingClientRect(),
            headingWidth = headingRect.width,
            headingHeight = $header.outerHeight(true);

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
        this.$section.css({ paddingTop: headingHeight });
        $header.addClass('heading-sticky').removeClass('not-sticky').css(newHeaderStyle);
      }
      this.isSticky = true;
    } else {
      this.clearStickiness();
    }
  }

  private resetHeaderWidth(): void {
    const $header = this.getHeader();
    if ($header && $header.get(0).className.includes('heading-sticky')) {
      $header.removeClass('heading-sticky').addClass('not-sticky').removeAttr('style').css({width: '', top: ''});
    }
  }

  private handleWindowResize(): void {
    this.resetHeaderWidth();
    this.positionHeader();
  }

  private destroyStickyBindings(): void {
    const $header = this.getHeader();
    this.$scrollableContainer.unbind('.stickyHeader-' + this.id);
    $(this.$window).unbind('.stickyHeader-' + this.id);
    this.$section.removeData();
    $header.removeData();
  }

  private clearStickiness(): void {
    if (this.isSticky) {
      if (this.notifyOnly) {
        this.$scope.$emit('sticky-header-disabled');
      } else {
        this.$section.css({ paddingTop: 0 });
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
export class StickyHeaderComponent implements IComponentOptions {
  public bindings: any = {
    addedOffsetHeight: '<',
    notifyOnly: '<',
    stickyIf: '<'
  };
  public controller: any = StickyHeaderController;
}

export const STICKY_HEADER_COMPONENT = 'spinnaker.core.utils.stickyHeader';
module(STICKY_HEADER_COMPONENT, [])
  .component('stickyHeader', new StickyHeaderComponent());
