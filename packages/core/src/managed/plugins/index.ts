import { constraintsManager, IConstraintHandler } from '../constraints/registry';
import { IResourceKindConfig, resourceManager } from '../resources/resourceRegistry';

export interface IManagedDeliveryPlugin {
  resources?: IResourceKindConfig[];
  constraints?: IConstraintHandler[];
}

export const registerManagedDeliveryPlugin = (plugin: IManagedDeliveryPlugin) => {
  plugin.resources?.forEach((resource) => resourceManager.registerHandler(resource));
  plugin.constraints?.forEach((constraint) => constraintsManager.registerHandler(constraint));
};
