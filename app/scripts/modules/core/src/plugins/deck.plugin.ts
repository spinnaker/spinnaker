import { toPairs } from 'lodash';

import { IStageTypeConfig } from 'core/domain';
import { HelpContentsRegistry } from 'core/help';
import { IResourceKindConfig, resourceManager } from 'core/managed';
import { Registry } from 'core/registry';
import { SearchResultType, searchResultTypeRegistry } from 'core/search';

export interface IDeckPlugin {
  /** Custom Stage UI (configuration and execution details) */
  stages?: IStageTypeConfig[];
  /** Custom Preconfigured Job Stage UI (configuration and execution details) */
  preconfiguredJobStages?: IStageTypeConfig[];
  /** Custom managed resource kinds */
  resourceKinds?: IResourceKindConfig[];
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
  plugin.resourceKinds?.forEach((kind) => resourceManager.registerResource(kind));
  toPairs(plugin.help ?? {}).forEach(([key, value]) => HelpContentsRegistry.register(key, value));
  plugin.search?.forEach((search) => searchResultTypeRegistry.register(search));

  // Run arbitrary plugin initialization code
  return Promise.resolve(plugin.initialize?.(plugin));
}
