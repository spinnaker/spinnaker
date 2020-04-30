import { module } from 'angular';
import { react2angular } from 'react2angular';

import { JsonEditor } from './JsonEditor';

export const JSON_EDITOR_COMPONENT = 'spinnaker.kubernetes.jsonEditor.component';
module(JSON_EDITOR_COMPONENT, []).component('jsonEditor', react2angular(JsonEditor, ['onChange', 'value']));
