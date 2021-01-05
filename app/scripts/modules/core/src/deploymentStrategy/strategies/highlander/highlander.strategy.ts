import { HighlanderPreview } from './HighlanderPreview';
import { DeploymentStrategyRegistry } from '../../deploymentStrategy.registry';

DeploymentStrategyRegistry.registerStrategy({
  label: 'Highlander',
  description:
    'Destroys <i>all</i> previous server groups in the cluster as soon as new server group passes health checks',
  key: 'highlander',
  AdditionalFieldsComponent: HighlanderPreview,
});
