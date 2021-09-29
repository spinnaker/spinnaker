import type { IConstraintHandler } from '../constraints/registry';
import { constraintsManager } from '../constraints/registry';
import type { IResourceKindConfig } from '../resources/resourceRegistry';
import { resourceManager } from '../resources/resourceRegistry';

export interface IManagedDeliveryPlugin {
  resources?: IResourceKindConfig[];
  constraints?: IConstraintHandler[];
}

export const registerManagedDeliveryPlugin = (plugin: IManagedDeliveryPlugin) => {
  plugin.resources?.forEach((resource) => resourceManager.registerHandler(resource));
  plugin.constraints?.forEach((constraint) => constraintsManager.registerHandler(constraint));
};
