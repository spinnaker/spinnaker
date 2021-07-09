import { toPairs } from 'lodash';

import { IStageTypeConfig } from '../domain';
import { HelpContentsRegistry } from '../help';
import { IManagedDeliveryPlugin, IResourceKindConfig, registerManagedDeliveryPlugin } from '../managed';
import { Registry } from '../registry';
import { SearchResultType, searchResultTypeRegistry } from '../search';

export interface IDeckPlugin {
  /** Custom Stage UI (configuration and execution details) */
  stages?: IStageTypeConfig[];
  /** Custom Preconfigured Job Stage UI (configuration and execution details) */
  preconfiguredJobStages?: IStageTypeConfig[];
  /** DEPRECATED - Custom managed resource kinds */
  resourceKinds?: IResourceKindConfig[];
  /** Managed Delivery hooks */
  managedDelivery?: IManagedDeliveryPlugin;
  /** Help Text for use in <HelpField /> */
  help?: { [helpKey: string]: string };
  /** Custom global search types */
  search?: SearchResultType[];

  initialize?(plugin: IDeckPlugin): void;
}

/** Given a plugin, registers the plugin's extensions with Deck registries */
export function registerPluginExtensions(plugin: IDeckPlugin): PromiseLike<any> {
  plugin.stages?.forEach((stage) => Registry.pipeline.registerStage(stage));
  plugin.preconfiguredJobStages?.forEach((stage) => Registry.pipeline.registerPreconfiguredJobStage(stage));
  toPairs(plugin.help ?? {}).forEach(([key, value]) => HelpContentsRegistry.register(key, value));
  plugin.search?.forEach((search) => searchResultTypeRegistry.register(search));

  if (plugin.managedDelivery || plugin.resourceKinds) {
    const managedDeliveryPlugin: IManagedDeliveryPlugin = {
      ...plugin.managedDelivery,
      resources: plugin.resourceKinds || plugin.managedDelivery?.resources,
    };
    registerManagedDeliveryPlugin(managedDeliveryPlugin);
  }
  // Run arbitrary plugin initialization code
  return Promise.resolve(plugin.initialize?.(plugin));
}
