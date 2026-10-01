import type { Mock } from 'vitest';
import { act, render, screen } from '@testing-library/react';
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
});
