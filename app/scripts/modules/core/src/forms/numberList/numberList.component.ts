import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from 'core/presentation';
import { NumberList } from './NumberList';

export const NUMBER_LIST_COMPONENT = 'spinnaker.core.forms.numberList';
module(NUMBER_LIST_COMPONENT, []).component(
  'numberList',
  react2angular(withErrorBoundary(NumberList, 'numberList'), ['constraints', 'label', 'model', 'onChange']),
);
