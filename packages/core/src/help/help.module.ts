import { module } from 'angular';

import './help.contents';
import { HELP_FIELD_COMPONENT } from './helpField.component';
import { HELP_FIELD_REACT_COMPONENT } from './helpFieldReact.component';

export const HELP_MODULE = 'spinnaker.core.help';
module(HELP_MODULE, [HELP_FIELD_COMPONENT, HELP_FIELD_REACT_COMPONENT]);
