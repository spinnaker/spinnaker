import type { Mock } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import $ from 'jquery';
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
    const { unmount } = render(
      <PipelineGraph pipeline={pipeline} viewState={{ section: 'triggers' } as any} onNodeClick={vi.fn()} />,
      { container },
    );
    const graphContainer = container.querySelector('div.pipeline-graph');
    const graph = container.querySelector('svg.pipeline-graph') as SVGElement;

    expect(resizeCallback).toBeDefined();
    expect(graph).toBeVisible();
    expect(graph.style.width).not.toBe('100%');
    expect(screen.getByText('Bake')).toBeVisible();

    container.style.width = '600px';
    act(() => {
      resizeCallback([{ target: graphContainer, contentRect: { width: 600 } } as any], {} as any);
    });

    expect(graph.style.width).toBe('100%');
    expect(screen.getByText('Bake')).toBeVisible();

    unmount();
  });

  it('retries layout when mounted before the element has width', () => {
    const { unmount } = render(
      <PipelineGraph pipeline={pipeline} viewState={{ section: 'triggers' } as any} onNodeClick={vi.fn()} />,
      { container },
    );
    const graph = container.querySelector('svg.pipeline-graph') as SVGElement;

    expect(graph.style.width).not.toBe('100%');
    expect(requestAnimationFrameSpy).toHaveBeenCalled();

    container.style.width = '600px';
    act(() => requestAnimationFrameSpy.mock.lastCall[0](0));

    expect(graph.style.width).toBe('100%');
    expect(screen.getByText('Bake')).toBeVisible();

    unmount();
  });

  it('skips the layout when only stage fields the graph does not draw change', () => {
    container.style.width = '600px';
    const viewState = { section: 'triggers' } as any;
    const onNodeClick = vi.fn();
    const { rerender, unmount } = render(
      <PipelineGraph pipeline={pipeline} viewState={viewState} onNodeClick={onNodeClick} />,
      { container },
    );
    // Layout measures each node's label
    const measureLabel = vi.spyOn($.fn, 'height');

    rerender(
      <PipelineGraph
        pipeline={{ ...pipeline, stages: [{ ...pipeline.stages[0], timeout: 300 }] }}
        viewState={viewState}
        onNodeClick={onNodeClick}
      />,
    );

    expect(measureLabel).not.toHaveBeenCalled();

    unmount();
  });

  it('lays out the graph again when a stage is renamed or its dependencies change', () => {
    container.style.width = '600px';
    const viewState = { section: 'triggers' } as any;
    const onNodeClick = vi.fn();
    const { rerender, unmount } = render(
      <PipelineGraph pipeline={pipeline} viewState={viewState} onNodeClick={onNodeClick} />,
      { container },
    );
    // Layout measures each node's label, including the configuration node
    const measureLabel = vi.spyOn($.fn, 'height');
    const update = (next: IPipeline) =>
      rerender(<PipelineGraph pipeline={next} viewState={viewState} onNodeClick={onNodeClick} />);

    update({ ...pipeline, stages: [{ ...pipeline.stages[0], name: 'Package' }] });

    expect(measureLabel).toHaveBeenCalledTimes(2);
    expect(screen.getByText('Package')).toBeVisible();

    const twoStages = {
      ...pipeline,
      stages: [
        { ...pipeline.stages[0], name: 'Package' },
        { refId: '2', name: 'Deploy', type: 'wait', requisiteStageRefIds: [] } as any,
      ],
    };
    update(twoStages);
    update({
      ...twoStages,
      stages: [twoStages.stages[0], { ...twoStages.stages[1], requisiteStageRefIds: ['1'] }],
    });

    expect(measureLabel).toHaveBeenCalledTimes(2 + 3 + 3);

    unmount();
  });
});
