import {module} from 'angular';
import * as $ from 'jquery';

export class ScrollToService {

  public constructor(private $timeout: ng.ITimeoutService) {}

  public scrollTo(selector: string, scrollableContainer: string, offset = 0, delay = 0): void {
    this.$timeout(() => {
      const elem: JQuery = $(selector);
      if (elem.length) {
        const content: JQuery = scrollableContainer ? elem.closest(scrollableContainer) : $('body');
        if (content.length) {
          const top: number = content.scrollTop() + elem.offset().top - offset;
          content.animate({scrollTop: top + 'px'}, 200);
        }
      }
    }, delay);
  }
}

export const SCROLL_TO_SERVICE = 'spinnaker.core.utils.scrollTo';

module(SCROLL_TO_SERVICE, [])
  .service('scrollToService', ScrollToService);
