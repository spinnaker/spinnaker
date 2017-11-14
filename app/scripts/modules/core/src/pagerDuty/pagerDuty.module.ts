import { module } from 'angular';

import { PAGE_MODAL_CONTROLLER } from './pageApplicationOwner.modal.controller';
import { PAGER_DUTY_WRITE_SERVICE } from './pagerDuty.write.service';

export const PAGER_DUTY_MODULE = 'spinnaker.core.pagerDuty';
module(PAGER_DUTY_MODULE, [
  PAGE_MODAL_CONTROLLER,
  PAGER_DUTY_WRITE_SERVICE,
]);
