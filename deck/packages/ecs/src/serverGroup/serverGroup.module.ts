import { module } from 'angular';

import { VIEW_EVENTS_LINK_COMPONENT } from './events/events.component';

export const ECS_SERVERGROUP_MODULE = 'spinnaker.ecs.serverGroup';
module(ECS_SERVERGROUP_MODULE, [VIEW_EVENTS_LINK_COMPONENT]);
