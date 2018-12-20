import { module } from 'angular';
import { react2angular } from 'react2angular';

import LabelFilter from './LabelFilter';

export const LABEL_FILTER_COMPONENT = 'spinnaker.core.labelFilter.component';
module(LABEL_FILTER_COMPONENT, []).component(
  'labelFilter',
  react2angular(LabelFilter, ['labelsMap', 'labelFilters', 'updateLabelFilters']),
);
