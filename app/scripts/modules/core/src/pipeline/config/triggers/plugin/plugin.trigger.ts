import { Registry } from 'core/registry';
import { PluginTrigger } from './PluginTrigger';

Registry.pipeline.registerTrigger({
  key: 'plugin',
  label: 'Plugin',
  description: 'Executes the pipeline in response to a Plugin Event',
  component: PluginTrigger,
});
