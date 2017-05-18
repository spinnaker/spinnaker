import { module } from 'angular';

import { HELP_FIELD_COMPONENT } from './helpField.component';
import { HELP_CONTENTS } from './help.contents';

export const HELP_MODULE = 'spinnaker.core.help';
module(HELP_MODULE, [
  HELP_CONTENTS,
  HELP_FIELD_COMPONENT
]);
