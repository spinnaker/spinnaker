import $ from 'jquery';

import { ScrollToService } from './scrollTo.service';

describe('ScrollToService', () => {
  let host: HTMLElement;

  beforeEach(() => {
    host = document.createElement('div');
    host.innerHTML = `
      <div class="container" style="height: 60px; overflow-y: scroll">
        <div style="height: 100px"></div>
        <div data-page-id="target">Target</div>
      </div>
    `;
    document.body.appendChild(host);
  });

  afterEach(() => {
    $.fx.off = false;
    host.remove();
  });

  it('schedules scrolls with a local native timeout', () => {
    $.fx.off = true;
    const timeout = spyOn(window, 'setTimeout').and.callFake((callback: TimerHandler) => {
      if (typeof callback === 'function') {
        callback();
      }
      return 0;
    });

    ScrollToService.scrollTo('[data-page-id=target]', '.container', 0, 25);

    expect(timeout).toHaveBeenCalledWith(jasmine.any(Function), 25);
  });
});
