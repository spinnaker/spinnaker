import { module } from 'angular';

import { ADD_ENTITY_TAG_LINKS_COMPONENT } from './addEntityTagLinks.component';
import { DATA_SOURCE_NOTIFICATIONS } from './notifications/DataSourceNotifications';
import { ENTITY_NOTIFICATIONS } from './notifications/EntityNotifications';
import { ENTITY_SOURCE_COMPONENT } from './entitySource.component';
import { ENTITY_TAG_WRITER } from './entityTags.write.service';

export const ENTITY_TAGS_MODULE = 'spinnaker.core.entityTags';
module(ENTITY_TAGS_MODULE, [
  ADD_ENTITY_TAG_LINKS_COMPONENT,
  DATA_SOURCE_NOTIFICATIONS,
  ENTITY_NOTIFICATIONS,
  ENTITY_SOURCE_COMPONENT,
  ENTITY_TAG_WRITER,
]);
