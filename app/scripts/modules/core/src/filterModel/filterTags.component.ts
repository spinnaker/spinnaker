import { module } from 'angular';
import { react2angular } from 'react2angular';
import { FilterTags } from './FilterTags';

export const FILTER_TAGS_COMPONENT = 'spinnaker.core.filterModel.filterTags.component';
module(FILTER_TAGS_COMPONENT, []).component('filterTags', react2angular(FilterTags, ['tags', 'tagCleared', 'clearFilters']));
