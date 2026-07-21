import { module } from 'angular';

import { AddEntityTagLinks } from './AddEntityTagLinks';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const ADD_ENTITY_TAG_LINKS_COMPONENT = 'spinnaker.core.entityTag.details.component';

module(ADD_ENTITY_TAG_LINKS_COMPONENT, []).component(
  'addEntityTagLinks',
  angularComponentFromReact(AddEntityTagLinks, 'addEntityTagLinks', [
    'application',
    'component',
    'entityType',
    'onUpdate',
    'ownerOptions',
    'tagType',
  ]),
);
