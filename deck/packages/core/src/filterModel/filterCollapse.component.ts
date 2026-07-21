import { module } from 'angular';

import { FilterCollapse } from './FilterCollapse';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const FILTER_COLLAPSE_COMPONENT = 'spinnaker.core.filterModel.filterCollapse.component';
module(FILTER_COLLAPSE_COMPONENT, []).component(
  'filterCollapse',
  angularComponentFromReact(FilterCollapse, 'filterCollapse'),
);
