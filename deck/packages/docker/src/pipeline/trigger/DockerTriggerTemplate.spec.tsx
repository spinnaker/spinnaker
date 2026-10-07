import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import { DockerTriggerTemplate } from './DockerTriggerTemplate';
import { DockerChartImageReader, DockerImageReader } from '../../image';

interface IDeferred<T> {
  promise: Promise<T>;
  resolve: (value: T) => void;
}

function deferred<T>(): IDeferred<T> {
  let resolve: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => (resolve = promiseResolve));
  return { promise, resolve };
}

// rxjs 7's real AsyncScheduler doesn't reliably advance under fake timers
// (a known, unresolved upstream issue: ReactiveX/rxjs#6382), so debounceTime
// is exercised with a real, short wait instead of a faked tick.
function tick(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

describe('<DockerTriggerTemplate/>', () => {
  async function selectReactOption(input: HTMLElement, option: string) {
    fireEvent.mouseDown(input);
    fireEvent.change(input, { target: { value: option } });
    await screen.findByRole('option', { name: option });
    fireEvent.keyDown(input, { key: 'Enter', code: 'Enter', keyCode: 13, which: 13 });
  }

  it('formats Docker trigger labels', async () => {
    await expectAsync(
      Promise.resolve(
        DockerTriggerTemplate.formatLabel({ account: 'prod-registry', repository: 'example/service' } as any),
      ),
    ).toBeResolvedTo('(Docker Registry) prod-registry: example/service');
  });

  it('writes docker image artifacts using tag references', async () => {
    const tag = '1.260101.000000-0000000';
    vi.spyOn(DockerImageReader, 'findTags').mockReturnValue(Promise.resolve([tag]));
    const updateCommand = vi.fn();
    render(
      <DockerTriggerTemplate
        command={{
          trigger: { type: 'docker', registry: 'registry.example.com', repository: 'example/service' },
        }}
        updateCommand={updateCommand}
      />,
    );
    await waitFor(() => expect(screen.getAllByRole('combobox')).toHaveLength(2));
    updateCommand.mockClear();

    await selectReactOption(screen.getAllByRole('combobox')[1], tag);

    expect(updateCommand.mock.calls).toEqual([
      ['extraFields.tag', tag],
      [
        'extraFields.artifacts',
        [
          {
            type: 'docker/image',
            name: 'registry.example.com/example/service',
            version: tag,
            reference: `registry.example.com/example/service:${tag}`,
          },
        ],
      ],
    ]);
  });

  it('writes Helm OCI image artifacts using digest references', async () => {
    vi.spyOn(DockerChartImageReader, 'findTags').mockReturnValue(Promise.resolve([]));
    const updateCommand = vi.fn();
    render(
      <DockerTriggerTemplate
        command={{
          trigger: { type: 'helm/oci', registry: 'registry.example.com', repository: 'charts/service' },
        }}
        updateCommand={updateCommand}
      />,
    );
    await screen.findByText('No tags found');
    await selectReactOption(screen.getAllByRole('combobox')[0], 'Digest');
    updateCommand.mockClear();

    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'sha256:abc123' } });

    expect(updateCommand.mock.calls).toEqual([
      ['extraFields.tag', 'sha256:abc123'],
      [
        'extraFields.artifacts',
        [
          {
            type: 'helm/image',
            name: 'registry.example.com/charts/service',
            version: 'sha256:abc123',
            reference: 'registry.example.com/charts/service@sha256:abc123',
          },
        ],
      ],
    ]);
  });

  it('aborts superseded and unmounted tag queries without publishing cancellation errors', async () => {
    const firstRequest = deferred<string[]>();
    const secondRequest = deferred<string[]>();
    const findTags = vi
      .spyOn(DockerImageReader, 'findTags')
      .mockReturnValueOnce(firstRequest.promise)
      .mockReturnValueOnce(secondRequest.promise);
    const consoleError = vi.spyOn(console, 'error');
    const updateCommand = vi.fn();
    const templateRef = React.createRef<DockerTriggerTemplate>();
    const rendered = render(
      <DockerTriggerTemplate
        ref={templateRef}
        command={{
          trigger: { type: 'docker', repository: 'example/service', tag: 'late' },
        }}
        updateCommand={updateCommand}
      />,
    );

    await act(() => tick(300));
    expect(findTags).toHaveBeenCalledTimes(1);
    const firstSignal = findTags.mock.calls[0][1] as AbortSignal;
    expect(firstSignal.aborted).toBe(false);

    // The template exposes no UI to re-query tags, so supersede the in-flight query directly.
    act(() => (templateRef.current as any).searchTags());
    await act(() => tick(300));
    expect(findTags).toHaveBeenCalledTimes(2);
    const secondSignal = findTags.mock.calls[1][1] as AbortSignal;

    expect(firstSignal.aborted).toBe(true);
    expect(secondSignal.aborted).toBe(false);

    await act(async () => {
      firstRequest.resolve(['stale']);
      await firstRequest.promise;
    });
    expect(screen.queryByText('stale')).not.toBeInTheDocument();
    expect(screen.queryByText('Error loading tags!')).not.toBeInTheDocument();

    updateCommand.mockClear();
    rendered.unmount();
    expect(secondSignal.aborted).toBe(true);

    await act(async () => {
      secondRequest.resolve(['late']);
      await secondRequest.promise;
    });

    expect(updateCommand).not.toHaveBeenCalled();
    expect(consoleError.mock.calls.flat().join(' ')).not.toContain('unmounted component');
  });
});
