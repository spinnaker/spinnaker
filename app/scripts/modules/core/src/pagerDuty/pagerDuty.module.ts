import { module } from 'angular';

import { PAGE_MODAL_CONTROLLER } from './pageApplicationOwner.modal.controller';
import { PAGER_DUTY_READ_SERVICE } from './pagerDuty.read.service';
import { PAGER_DUTY_SELECT_FIELD_COMPONENT } from './pagerDutySelectField.component';
import { PAGER_DUTY_TAG_COMPONENT } from './pagerDutyTag.component';
import { PAGER_DUTY_WRITE_SERVICE } from './pagerDuty.write.service';

export const PAGER_DUTY_MODULE = 'spinnaker.core.pagerDuty';
module(PAGER_DUTY_MODULE, [
  PAGE_MODAL_CONTROLLER,
  PAGER_DUTY_READ_SERVICE,
  PAGER_DUTY_SELECT_FIELD_COMPONENT,
  PAGER_DUTY_TAG_COMPONENT,
  PAGER_DUTY_WRITE_SERVICE,
]);
