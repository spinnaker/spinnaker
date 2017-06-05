declare const System: any;
import { UIRouter, Glob } from '@uirouter/core';

import { bootstrapModule } from './bootstrap.module';
import { paramChangedHelper } from 'core/bootstrap';

/**
 * Toggles the @uirouter/visualizer based on query parameter `vis` changing
 * Type javascript:vis() in the browser url or add `&vis=true` to the spinnaker URL
 */
bootstrapModule.run(($uiRouter: UIRouter) => {
  'ngInject';

  const launchVisualizer = () => {
    // Auto-collapse certain states with lots of children
    const collapseGlobs = ['home.*', 'home.*.application.*', 'home.*.application.insight.*'].map(globStr => new Glob(globStr));
    const collapsedStates = $uiRouter.stateRegistry.get().filter(state => collapseGlobs.some(glob => glob.matches(state.name)));
    collapsedStates.forEach(state => (state.$$state() as any)._collapsed = true);

    return System.import('@uirouter/visualizer').then((vis: any) => $uiRouter.plugin(vis.Visualizer));
  };

  const toggleVisualizer = (enabled: boolean) => {
    if (enabled) {
      return launchVisualizer();
    } else {
      const plugin = $uiRouter.getPlugin('visualizer');
      plugin && $uiRouter.dispose(plugin);
    }
  };

  (window as any).vis = launchVisualizer;
  $uiRouter.transitionService.onBefore({}, paramChangedHelper('vis', toggleVisualizer));
});

