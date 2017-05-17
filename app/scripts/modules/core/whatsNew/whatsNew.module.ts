import { module } from 'angular';

export const WHATS_NEW_MODULE = 'spinnaker.core.whatsNew';
module(WHATS_NEW_MODULE, [
  require('./whatsNew.directive'),
]);
