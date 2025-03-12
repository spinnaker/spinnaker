import { PluginTrigger } from './PluginTrigger';
import { Registry } from '../../../../registry';

Registry.pipeline.registerTrigger({
  key: 'plugin',
  label: 'Plugin',
  description: 'Executes the pipeline in response to a Plugin Event',
  component: PluginTrigger,
});
