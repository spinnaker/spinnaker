import { module } from 'angular';
import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';
import { react2angular } from 'react2angular';

import { YamlEditor } from './YamlEditor';

export const YAML_EDITOR_COMPONENT = 'spinnaker.core.yamlEditor.component';
module(YAML_EDITOR_COMPONENT, []).component(
  'yamlEditor',
  react2angular(withErrorBoundary(YamlEditor, 'yamlEditor'), ['onChange', 'value']),
);
