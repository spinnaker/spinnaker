import { module } from 'angular';

import { ADD_ENTITY_TAG_LINKS_COMPONENT } from './addEntityTagLinks.component';
import { DATA_SOURCE_ALERTS_COMPONENT } from './dataSourceAlerts.component';
import { ENTITY_SOURCE_COMPONENT } from './entitySource.component';
import { ENTITY_UI_TAGS_COMPONENT } from './entityUiTags.component';

export const ENTITY_TAGS_MODULE = 'spinnaker.core.entityTags';
module(ENTITY_TAGS_MODULE, [
  ADD_ENTITY_TAG_LINKS_COMPONENT,
  DATA_SOURCE_ALERTS_COMPONENT,
  ENTITY_SOURCE_COMPONENT,
  ENTITY_UI_TAGS_COMPONENT,
]);
