import {ITimeoutService, element, module} from 'angular';

import {NetflixSettings} from '../netflix.settings';

import './blesk.less';

class BleskService {

  private static BLESK_DIV =
    '<div class="blesk-wrapper"><div id="blesk" class="container" data-appid="spinnaker"></div></div>';
  private static BLESK_SCRIPT =
    '<script async src="https://blesk.prod.netflix.net/static/js/blesk.js"></script>';

  public initialize(): void {
    if (element('.spinnaker-content').length && !element('#blesk').length) {
      element('.spinnaker-content').prepend(BleskService.BLESK_DIV);
      element('body').append(BleskService.BLESK_SCRIPT);
    }
  }
}

export const BLESK_MODULE = 'spinnaker.netflix.blesk';
module(BLESK_MODULE, [])
  .service('blesk', BleskService)
  .run(($timeout: ITimeoutService, blesk: BleskService) => {
    if (NetflixSettings.feature.blesk) {
      $timeout(blesk.initialize, 5000); // delay on init so auth can occur and DOM can finish loading
    }
  });
