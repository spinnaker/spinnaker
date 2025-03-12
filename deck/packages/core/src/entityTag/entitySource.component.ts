import { module } from 'angular';
import { react2angular } from 'react2angular';

import { EntitySource } from './EntitySource';
import { withErrorBoundary } from '../presentation';

export const ENTITY_SOURCE_COMPONENT = 'spinnaker.core.entityTag.entitySource.component';
module(ENTITY_SOURCE_COMPONENT, []).component(
  'entitySource',
  react2angular(withErrorBoundary(EntitySource, 'entitySource'), ['metadata', 'relativePath']),
);
