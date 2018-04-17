import { module } from 'angular';

import { HELP_FIELD_COMPONENT } from './helpField.component';
import './help.contents';

export const HELP_MODULE = 'spinnaker.core.help';
module(HELP_MODULE, [HELP_FIELD_COMPONENT]);
