import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from 'core/presentation';
import { EntitySource } from './EntitySource';

export const ENTITY_SOURCE_COMPONENT = 'spinnaker.core.entityTag.entitySource.component';
module(ENTITY_SOURCE_COMPONENT, []).component(
  'entitySource',
  react2angular(withErrorBoundary(EntitySource, 'entitySource'), ['metadata', 'relativePath']),
);
