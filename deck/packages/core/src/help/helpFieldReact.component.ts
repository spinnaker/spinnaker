import { module } from 'angular';

import { HelpField } from './HelpField';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const HELP_FIELD_REACT_COMPONENT = 'spinnaker.core.help.helpFieldReact.component';

module(HELP_FIELD_REACT_COMPONENT, []).component(
  'helpFieldReact',
  angularComponentFromReact(HelpField, 'helpFieldReact', ['content', 'expand', 'fallback', 'id', 'label', 'placement']),
);
