import type { Mock } from 'vitest';
import { mount } from 'enzyme';
import React from 'react';

import type { IPipeline } from '../../../domain';
import { PipelineGraph } from './PipelineGraph';

describe('PipelineGraph', () => {
  let container: HTMLDivElement;
  let originalResizeObserver: typeof ResizeObserver;
  let originalRequestAnimationFrame: typeof requestAnimationFrame;
  let requestAnimationFrameSpy: Mock;
  let resizeCallback: ResizeObserverCallback;

  const pipeline: IPipeline = {
    application: 'app',
    id: 'pipeline-id',
    name: 'Pipeline',
    stages: [{ refId: '1', name: 'Bake', type: 'bake', requisiteStageRefIds: [] } as any],
    triggers: [],
    parameterConfig: [],
    notifications: [],
    limitConcurrent: true,
    keepWaitingPipelines: false,
  };

  beforeEach(() => {
    container = document.createElement('div');
    container.style.width = '0px';
    document.body.appendChild(container);
    originalResizeObserver = window.ResizeObserver;
    originalRequestAnimationFrame = window.requestAnimationFrame;
    requestAnimationFrameSpy = vi.spyOn(window, 'requestAnimationFrame').mockReturnValue(1);
    (window as any).ResizeObserver = class {
      public observe = vi.fn();
      public disconnect = vi.fn();

      constructor(callback: ResizeObserverCallback) {
        resizeCallback = callback;
      }
    };
  });

  afterEach(() => {
    window.ResizeObserver = originalResizeObserver;
    window.requestAnimationFrame = originalRequestAnimationFrame;
    container.remove();
    resizeCallback = undefined;
  });

  it('recalculates graph layout when the element receives width after mounting', () => {
    const wrapper = mount(
      <PipelineGraph pipeline={pipeline} viewState={{ section: 'triggers' } as any} onNodeClick={vi.fn()} />,
      { attachTo: container },
    );
    const graph = wrapper.find(PipelineGraph).instance() as PipelineGraph;

    expect(resizeCallback).toBeDefined();
    expect(graph.state.graphWidth).not.toBe('100%');

    container.style.width = '600px';
    resizeCallback(
      [{ target: wrapper.find('div.pipeline-graph').getDOMNode(), contentRect: { width: 600 } } as any],
      {} as any,
    );
    wrapper.update();

    expect(graph.state.graphWidth).toBe('100%');

    wrapper.unmount();
  });

  it('retries layout when mounted before the element has width', () => {
    const wrapper = mount(
      <PipelineGraph pipeline={pipeline} viewState={{ section: 'triggers' } as any} onNodeClick={vi.fn()} />,
      { attachTo: container },
    );
    const graph = wrapper.find(PipelineGraph).instance() as PipelineGraph;

    expect(graph.state.graphWidth).not.toBe('100%');
    expect(requestAnimationFrameSpy).toHaveBeenCalled();

    container.style.width = '600px';
    requestAnimationFrameSpy.mock.lastCall[0](0);
    wrapper.update();

    expect(graph.state.graphWidth).toBe('100%');

    wrapper.unmount();
  });
});
