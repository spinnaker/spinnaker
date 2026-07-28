import { toPairs } from 'lodash';

import type { IStageTypeConfig } from '../domain';
import { HelpContentsRegistry } from '../help';
import type { IManagedDeliveryPlugin, IResourceKindConfig } from '../managed';
import { registerManagedDeliveryPlugin } from '../managed';
import { Registry } from '../registry';
import type { SearchResultType } from '../search';
import { searchResultTypeRegistry } from '../search';

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

  initialize?(plugin: IDeckPlugin): void | PromiseLike<void>;
}

/** Given a plugin, registers the plugin's extensions with Deck registries */
export async function registerPluginExtensions(plugin: IDeckPlugin): Promise<void> {
  const attempts: Array<() => unknown | PromiseLike<unknown>> = [
    ...(plugin.stages ?? []).map((stage) => () => Registry.pipeline.registerStage(stage)),
    ...(plugin.preconfiguredJobStages ?? []).map((stage) => () =>
      Registry.pipeline.registerPreconfiguredJobStage(stage),
    ),
    ...toPairs(plugin.help ?? {}).map(([key, value]) => () => HelpContentsRegistry.register(key, value)),
    ...(plugin.search ?? []).map((search) => () => searchResultTypeRegistry.register(search)),
  ];

  if (plugin.managedDelivery || plugin.resourceKinds) {
    attempts.push(() => {
      const managedDeliveryPlugin: IManagedDeliveryPlugin = {
        ...plugin.managedDelivery,
        resources: plugin.resourceKinds || plugin.managedDelivery?.resources,
      };
      registerManagedDeliveryPlugin(managedDeliveryPlugin);
    });
  }
  attempts.push(() => plugin.initialize?.(plugin));

  const work = attempts.map((attempt) => {
    try {
      return Promise.resolve(attempt());
    } catch (error) {
      return Promise.reject(error);
    }
  });
  let rejected = false;
  let rejectionReason: unknown;
  await Promise.all(
    work.map((promise) =>
      promise.catch((error) => {
        if (!rejected) {
          rejected = true;
          rejectionReason = error;
        }
      }),
    ),
  );
  if (rejected) {
    throw rejectionReason;
  }
}
