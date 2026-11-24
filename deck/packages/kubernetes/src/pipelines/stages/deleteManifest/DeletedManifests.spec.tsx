import { render as rtlRender } from '@testing-library/react';
import React from 'react';

import type { IExecutionStage } from '@spinnaker/core';

import { DeletedManifests } from './DeletedManifests';

// Markdown injects its HTML with dangerouslySetInnerHTML, so query the rendered DOM directly.
const codeBlock = (container: HTMLElement): HTMLElement => container.querySelector('pre');
const text = (container: HTMLElement, selector: string): string => container.querySelector(selector)?.textContent;
const count = (container: HTMLElement, selector: string): number => container.querySelectorAll(selector).length;

const render = (stage: Partial<IExecutionStage>) =>
  rtlRender(<div>{<DeletedManifests stage={stage as IExecutionStage} />}</div>).container;

describe('<DeletedManifests />', () => {
  it('lists deleted objects with their namespaces across multiple namespaces', () => {
    const container = render({
      status: 'SUCCEEDED',
      context: {
        'outputs.manifestNamesByNamespace': {
          prod: ['deployment my-app', 'service my-app'],
          staging: ['deployment my-app'],
        },
      },
    });
    expect(text(container, 'dt')).toEqual('Deleted');
    expect(codeBlock(container).textContent).toEqual(
      ['deployment my-app', 'service my-app', 'deployment my-app'].join('\n') + '\n',
    );
    expect(count(container, 'dl.dl-horizontal')).toEqual(0);
  });

  it('skips namespaces with an empty list', () => {
    const container = render({
      status: 'SUCCEEDED',
      context: { 'outputs.manifestNamesByNamespace': { empty: [], prod: ['service a'] } },
    });
    expect(codeBlock(container).textContent).toEqual('service a\n');
  });

  it('lists objects under an empty namespace key (cluster-scoped)', () => {
    const container = render({
      status: 'SUCCEEDED',
      context: { 'outputs.manifestNamesByNamespace': { '': ['namespace team-a'], prod: ['service a'] } },
    });
    expect(codeBlock(container).textContent).toEqual('namespace team-a\nservice a\n');
  });

  it('does not use stage.outputs as a source', () => {
    const container = render({
      status: 'SUCCEEDED',
      context: {},
      outputs: { 'outputs.manifestNamesByNamespace': { prod: ['configMap cfg'] } },
    });
    expect(codeBlock(container)).toBeNull();
    expect(text(container, 'dd')).toEqual('No resources deleted');
  });

  it('shows "No resources deleted" when the stage is complete and the list is empty', () => {
    const container = render({ status: 'SUCCEEDED', context: { 'outputs.manifestNamesByNamespace': {} } });
    expect(text(container, 'dt')).toEqual('Deleted');
    expect(text(container, 'dd')).toEqual('No resources deleted');
    expect(codeBlock(container)).toBeNull();
  });

  it('shows "No resources deleted" when a completed stage has no output at all', () => {
    const container = render({ status: 'SUCCEEDED', context: {} });
    expect(text(container, 'dd')).toEqual('No resources deleted');
  });

  it('renders nothing while the stage has not produced output yet', () => {
    const container = render({ status: 'RUNNING', context: {} });
    expect(count(container, 'dt')).toEqual(0);
    expect(count(container, 'dd')).toEqual(0);
  });

  it('renders nothing when the stage failed or was canceled with no output', () => {
    ['TERMINAL', 'CANCELED'].forEach((status) => {
      const container = render({ status, context: {} } as any);
      expect(count(container, 'dt')).toEqual(0);
      expect(count(container, 'dd')).toEqual(0);
    });
  });
});
