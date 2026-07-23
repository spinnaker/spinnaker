import { module } from 'angular';

import { FilterTags } from './FilterTags';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const FILTER_TAGS_COMPONENT = 'spinnaker.core.filterModel.filterTags.component';
module(FILTER_TAGS_COMPONENT, []).component(
  'filterTags',
  angularComponentFromReact(FilterTags, 'filterTags', ['tags', 'tagCleared', 'clearFilters']),
);
