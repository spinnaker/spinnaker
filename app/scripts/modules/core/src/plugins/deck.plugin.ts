import { IStageTypeConfig } from 'core/domain';
import { Registry } from 'core/registry';
import { SearchResultType } from 'core/search';

export interface IDeckPlugin {
  stages?: IStageTypeConfig[];
  preconfiguredJobStages?: IStageTypeConfig[];
  helpContents?: { [helpKey: string]: string };
  searchResultTypes?: SearchResultType[];

  initialize?(): void;
}

/** Given a plugin, registers the plugin's extensions */
export function registerPluginExtensions(plugin: IDeckPlugin) {
  // Register the plugin's extensions with deck.
  plugin.stages?.forEach(stage => Registry.pipeline.registerStage(stage));
  plugin.preconfiguredJobStages?.forEach(stage => Registry.pipeline.registerPreconfiguredJobStage(stage));

  // Run code that currently does not have an extension point
  plugin.initialize?.();
}
