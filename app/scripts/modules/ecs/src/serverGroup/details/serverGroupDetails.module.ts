import { module } from 'angular';
import { ECS_SERVERGROUP_DETAILS_SERVERGROUPDETAILS_ECS_CONTROLLER } from './serverGroupDetails.ecs.controller';

export const SERVER_GROUP_DETAILS_MODULE = 'spinnaker.ecs.serverGroup.details';
module(SERVER_GROUP_DETAILS_MODULE, [ECS_SERVERGROUP_DETAILS_SERVERGROUPDETAILS_ECS_CONTROLLER]);
