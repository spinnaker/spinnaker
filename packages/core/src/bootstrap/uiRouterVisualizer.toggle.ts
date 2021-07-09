import { Glob, Transition, UIRouter, UIRouterPlugin } from '@uirouter/core';

import { bootstrapModule } from './bootstrap.module';

/**
 * Toggles the @uirouter/visualizer based on query parameter `vis` changing
 * Type javascript:vis() in the browser url or add `&vis=true` to the spinnaker URL
 */
bootstrapModule.run([
  '$uiRouter',
  ($uiRouter: UIRouter) => {
    let visualizerEnabled: 'true' | 'false' = 'false';
    let VisualizerPlugin: { new (): UIRouterPlugin } = null;

    function loadVisualizer() {
      // Auto-collapse certain states with lots of children
      const collapseGlobs = ['home.*', 'home.*.application.*', 'home.*.application.insight.*'].map(
        (globStr) => new Glob(globStr),
      );
      const collapsedStates = $uiRouter.stateRegistry
        .get()
        .filter((state) => collapseGlobs.some((glob) => glob.matches(state.name)));
      collapsedStates.forEach((state) => ((state.$$state() as any)._collapsed = true));

      return import('@uirouter/visualizer')
        .then((vis: any) => (VisualizerPlugin = vis.Visualizer))
        .then(createVisualizer);
    }

    function createVisualizer() {
      if (visualizerEnabled !== 'true') {
        return;
      }

      // Cleanup any current visualizer first
      destroyVisualizer();

      if (VisualizerPlugin) {
        $uiRouter.plugin(VisualizerPlugin);
      } else {
        loadVisualizer();
      }
    }

    function destroyVisualizer() {
      const plugin = $uiRouter.getPlugin('visualizer');
      plugin && $uiRouter.dispose(plugin);
    }

    function toggleVisualizer(trans: Transition) {
      const enabled: 'true' | 'false' = trans.paramsChanged().vis;
      if (enabled === undefined) {
        return null;
      }

      if (enabled === visualizerEnabled) {
        return trans.targetState().withParams({ vis: undefined });
      }

      visualizerEnabled = enabled;

      if (enabled === 'true') {
        createVisualizer();
      } else if (enabled === 'false') {
        destroyVisualizer();
      }

      return trans.targetState().withParams({ vis: undefined });
    }

    (window as any).vis = createVisualizer;
    $uiRouter.transitionService.onStart({}, toggleVisualizer);
  },
]);
