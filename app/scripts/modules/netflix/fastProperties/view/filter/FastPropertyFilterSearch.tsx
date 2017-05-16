import { Subject } from 'rxjs/Subject';

import { Property } from '../../domain/property.domain';
import { IFilterTag } from 'core/filterModel/FilterTags';

export interface IFastPropertyProps {
  properties: Property[];
  filtersUpdatedStream: Subject<IFilterTag[]>;
}
