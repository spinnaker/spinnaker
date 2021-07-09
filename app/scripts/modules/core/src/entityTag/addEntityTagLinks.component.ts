import { module } from 'angular';
import { react2angular } from 'react2angular';

import { AddEntityTagLinks } from './AddEntityTagLinks';
import { withErrorBoundary } from '../presentation';

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
