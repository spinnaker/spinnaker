import { module } from 'angular';

import { HELP_FIELD_COMPONENT } from './helpField.component';

export const HELP_MODULE = 'spinnaker.core.help';
module(HELP_MODULE, [
  require('./helpContents'),
  HELP_FIELD_COMPONENT
]);
