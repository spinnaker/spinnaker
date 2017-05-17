import { module } from 'angular';

import { CANCEL_MODAL_SERVICE } from './cancelModal.service';

export const CANCEL_MODAL_MODULE = 'spinnaker.core.cancelModal';
module(CANCEL_MODAL_MODULE, [
  CANCEL_MODAL_SERVICE,
]);
