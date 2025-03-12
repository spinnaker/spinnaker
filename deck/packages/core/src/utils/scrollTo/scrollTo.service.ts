import $ from 'jquery';
import { $timeout } from 'ngimport';

export class ScrollToService {
  public static toDomId(id: string) {
    return id.replace(/[\W]/g, '-');
  }

  public static scrollTo(selector: string, scrollableContainer: string, offset = 0, delay = 0): void {
    $timeout(() => {
      const elem: JQuery = $(selector);
      if (elem.length) {
        const content: JQuery = scrollableContainer ? elem.closest(scrollableContainer) : $('body');
        if (content.length) {
          const top: number = content.scrollTop() + elem.offset().top - offset;
          content.animate({ scrollTop: top + 'px' }, 200);
        }
      }
    }, delay);
  }
}
