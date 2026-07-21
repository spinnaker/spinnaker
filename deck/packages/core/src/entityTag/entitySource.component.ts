import { module } from 'angular';

import { EntitySource } from './EntitySource';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const ENTITY_SOURCE_COMPONENT = 'spinnaker.core.entityTag.entitySource.component';
module(ENTITY_SOURCE_COMPONENT, []).component(
  'entitySource',
  angularComponentFromReact(EntitySource, 'entitySource', ['metadata', 'relativePath']),
);
