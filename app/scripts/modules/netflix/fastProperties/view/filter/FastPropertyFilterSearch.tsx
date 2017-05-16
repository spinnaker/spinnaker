import * as React from 'react';
import { angular2react } from 'angular2react';
import { Subject } from 'rxjs/Subject';

import { Property } from '../../domain/property.domain';
import { IFilterTag } from 'core/filterModel/FilterTags';
import { ReactInjector } from 'core/react';
import { fastPropertyFilterSearchComponent } from './fastPropertyFilterSearch.component';

interface IProps {
  properties: Property[];
  filtersUpdatedStream: Subject<IFilterTag[]>;
}

export let FastPropertyFilterSearch: React.ComponentClass<IProps> = undefined;
ReactInjector.give(($injector: any) => FastPropertyFilterSearch = angular2react('fastPropertyFilterSearch', fastPropertyFilterSearchComponent, $injector) as any);
