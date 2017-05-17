import {module} from 'angular';

export const APPLICATION_BOOTSTRAP_MODULE = 'spinnaker.core.applicationBootstrap';
module(APPLICATION_BOOTSTRAP_MODULE, [
  require('./applicationBootstrap.directive'),
]);
