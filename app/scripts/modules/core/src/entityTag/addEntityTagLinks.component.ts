import { module } from 'angular';
import { react2angular } from 'react2angular';
import { withErrorBoundary } from 'core/presentation';

import { AddEntityTagLinks } from './AddEntityTagLinks';

export const ADD_ENTITY_TAG_LINKS_COMPONENT = 'spinnaker.core.entityTag.details.component';

module(ADD_ENTITY_TAG_LINKS_COMPONENT, []).component(
  'addEntityTagLinks',
  react2angular(withErrorBoundary(AddEntityTagLinks, 'addEntityTagLinks'), [
    'application',
    'component',
    'entityType',
    'onUpdate',
    'ownerOptions',
    'tagType',
  ]),
);
