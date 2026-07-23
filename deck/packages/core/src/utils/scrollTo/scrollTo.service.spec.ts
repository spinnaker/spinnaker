import $ from 'jquery';

import { AngularServices } from '../../angular/services';
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

  it('schedules scrolls through the AngularServices timeout fallback', () => {
    $.fx.off = true;
    const timeout = jasmine.createSpy('$timeout').and.callFake((callback: () => void) => callback());
    spyOnProperty(AngularServices, '$timeout', 'get').and.returnValue(timeout as any);

    ScrollToService.scrollTo('[data-page-id=target]', '.container');

    expect(timeout).toHaveBeenCalled();
  });
});
