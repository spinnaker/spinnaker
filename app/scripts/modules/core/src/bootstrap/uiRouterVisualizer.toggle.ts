declare const System: any;
import { UIRouter, Glob, UIRouterPlugin } from '@uirouter/core';

import { bootstrapModule } from './bootstrap.module';
import { paramChangedHelper } from 'core/bootstrap';

/**
 * Toggles the @uirouter/visualizer based on query parameter `vis` changing
 * Type javascript:vis() in the browser url or add `&vis=true` to the spinnaker URL
 */
bootstrapModule.run(($uiRouter: UIRouter) => {
  'ngInject';

  let visualizerEnabled = false;
  let VisualizerPlugin: { new (): UIRouterPlugin } = null;

  const loadVisualizer = () => {
    // Auto-collapse certain states with lots of children
    const collapseGlobs = ['home.*', 'home.*.application.*', 'home.*.application.insight.*'].map(
      globStr => new Glob(globStr),
    );
    const collapsedStates = $uiRouter.stateRegistry
      .get()
      .filter(state => collapseGlobs.some(glob => glob.matches(state.name)));
    collapsedStates.forEach(state => ((state.$$state() as any)._collapsed = true));

    return System.import('@uirouter/visualizer')
      .then((vis: any) => (VisualizerPlugin = vis.Visualizer))
      .then(createVisualizer);
  };

  const createVisualizer = () => {
    if (!visualizerEnabled) {
      return;
    }

    // Cleanup any current visualizer first
    destroyVisualizer();

    if (VisualizerPlugin) {
      $uiRouter.plugin(VisualizerPlugin);
    } else {
      loadVisualizer();
    }
  };

  const destroyVisualizer = () => {
    const plugin = $uiRouter.getPlugin('visualizer');
    plugin && $uiRouter.dispose(plugin);
  };

  const toggleVisualizer = (enabled: boolean) => {
    if (enabled === visualizerEnabled) {
      return;
    }
    visualizerEnabled = enabled;

    if (enabled) {
      return createVisualizer();
    } else {
      return destroyVisualizer();
    }
  };

  (window as any).vis = createVisualizer;
  $uiRouter.transitionService.onBefore({}, paramChangedHelper('vis', toggleVisualizer));
});
