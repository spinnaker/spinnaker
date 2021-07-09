import { module } from 'angular';
import { react2angular } from 'react2angular';

import { YamlEditor } from './YamlEditor';
import { withErrorBoundary } from '../presentation/SpinErrorBoundary';

export const YAML_EDITOR_COMPONENT = 'spinnaker.core.yamlEditor.component';
module(YAML_EDITOR_COMPONENT, []).component(
  'yamlEditor',
  react2angular(withErrorBoundary(YamlEditor, 'yamlEditor'), ['onChange', 'value']),
);
