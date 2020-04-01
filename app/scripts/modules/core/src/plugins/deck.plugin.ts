import { toPairs } from 'lodash';
import { IStageTypeConfig } from 'core/domain';
import { HelpContentsRegistry } from 'core/help';
import { Registry } from 'core/registry';
import { SearchResultType, searchResultTypeRegistry } from 'core/search';

export interface IDeckPlugin {
  stages?: IStageTypeConfig[];
  preconfiguredJobStages?: IStageTypeConfig[];
  help?: { [helpKey: string]: string };
  search?: SearchResultType[];

  initialize?(): void;
}

/** Given a plugin, registers the plugin's extensions */
export function registerPluginExtensions(plugin: IDeckPlugin): PromiseLike<any> {
  // Register the plugin's extensions with deck.
  plugin.stages?.forEach(stage => Registry.pipeline.registerStage(stage));
  plugin.preconfiguredJobStages?.forEach(stage => Registry.pipeline.registerPreconfiguredJobStage(stage));
  toPairs(plugin.help ?? {}).forEach(([key, value]) => HelpContentsRegistry.register(key, value));
  plugin.search?.forEach(search => searchResultTypeRegistry.register(search));

  // Run arbitrary initialization code
  return Promise.resolve(plugin.initialize?.());
}
