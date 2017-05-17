import {module} from 'angular';

export const ANALYTICS_MODULE = 'spinnaker.core.analytics';
module(ANALYTICS_MODULE, [
  require('./analytics.service'),
]);
