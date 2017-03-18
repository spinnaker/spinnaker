import {ITimeoutService, element, module} from 'angular';

require('./bleskOverrides.css');

class BleskService {

  private static BLESK_DIV =
    '<div id="blesk" class="container" data-appid="spinnaker" style="flex: 0 0 auto; padding: 0;"></div>';
  private static BLESK_SCRIPT =
    '<script async src="https://blesk.prod.netflix.net/static/js/blesk.js"></script>';

  public initialize(): void {
    if (element('.spinnaker-header').length && !element('#blesk').length) {
      element('.spinnaker-header').after(BleskService.BLESK_DIV);
      element('body').append(BleskService.BLESK_SCRIPT);
    }
  }
}

export const BLESK_MODULE = 'spinnaker.netflix.blesk';
module(BLESK_MODULE, [require('core/config/settings.js')])
  .service('blesk', BleskService)
  .run(($timeout: ITimeoutService, settings: any, blesk: BleskService) => {
    if (settings.feature && settings.feature.blesk) {
      $timeout(blesk.initialize, 5000); // delay on init so auth can occur and DOM can finish loading
    }
  });
