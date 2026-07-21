import { module } from 'angular';

import { YamlEditor } from './YamlEditor';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const YAML_EDITOR_COMPONENT = 'spinnaker.core.yamlEditor.component';
module(YAML_EDITOR_COMPONENT, []).component(
  'yamlEditor',
  angularComponentFromReact(YamlEditor, 'yamlEditor', ['onChange', 'value']),
);
