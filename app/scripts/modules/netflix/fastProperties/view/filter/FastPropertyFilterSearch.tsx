import { Subject } from 'rxjs';

import { IFilterTag } from '@spinnaker/core';

import { Property } from '../../domain/property.domain';

export interface IFastPropertyProps {
  properties: Property[];
  filtersUpdatedStream: Subject<IFilterTag[]>;
}
