import { module } from 'angular';

export const REGION_MODULE = 'spinnaker.core.region.module';
module(REGION_MODULE, [
  require('./regionSelectField.directive'),
]);
