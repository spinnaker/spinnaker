import { module } from 'angular';

import LabelFilter from './LabelFilter';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const LABEL_FILTER_COMPONENT = 'spinnaker.core.labelFilter.component';
module(LABEL_FILTER_COMPONENT, []).component(
  'labelFilter',
  angularComponentFromReact(LabelFilter, 'labelFilter', ['labelsMap', 'labelFilters', 'updateLabelFilters']),
);
