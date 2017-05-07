import * as React from 'react';
import { angular2react } from 'angular2react';

import { filterTagsComponent } from './filterTags.component';
import { ReactInjector } from 'core/react.module';

export interface IFilter {
  label: string;
  value: string;
}

export interface IFilterTag extends IFilter {
  clear: () => any;
}

interface IProps {
  tags: IFilterTag[];
  tagCleared?: () => any;
  clearFilters: () => any;
}

export let FilterTags: React.ComponentClass<IProps> = undefined;
ReactInjector.give(($injector: any) => FilterTags = angular2react('filterTags', filterTagsComponent, $injector) as any);
