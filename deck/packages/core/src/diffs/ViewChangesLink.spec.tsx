import { UIRouterReact } from '@uirouter/react';
import { act, render, screen, waitFor } from '@testing-library/react';
import type { RenderResult } from '@testing-library/react';
import { setupUser } from '../utils/testUtils/userEvent';
import React from 'react';

import { ViewChangesLink } from './ViewChangesLink';
import type { IViewChangesConfig } from './ViewChangesLink';
import type { ICommit } from './CommitHistory';
import { createDeckRuntime } from '../bootstrap/DeckRuntime';
import type { DeckRuntime } from '../bootstrap/DeckRuntime';
import { DeckRuntimeContext } from '../bootstrap/DeckRuntimeContext';
import type { ICreationMetadata, ICreationMetadataTag, IExecution } from '../domain';

const commit = (id: string): ICommit => ({
  authorDisplayName: 'Developer',
  commitUrl: `https://example.com/commits/${id}`,
  displayId: id,
  id,
  message: `Commit ${id}`,
  timestamp: 0,
});

const metadata = (value: ICreationMetadata): ICreationMetadataTag => ({
  name: 'spinnaker:metadata',
  value,
});

describe('ViewChangesLink', () => {
  let router: UIRouterReact;
  let runtime: DeckRuntime;

  const renderLink = (changeConfig: IViewChangesConfig): RenderResult =>
    render(
      <DeckRuntimeContext.Provider value={runtime}>
        <ViewChangesLink changeConfig={changeConfig} nameItem={{ name: 'Deploy' }} viewType="linkOnly" />
      </DeckRuntimeContext.Provider>,
    );

  const rerenderLink = (view: RenderResult, changeConfig: IViewChangesConfig) =>
    view.rerender(
      <DeckRuntimeContext.Provider value={runtime}>
        <ViewChangesLink changeConfig={changeConfig} nameItem={{ name: 'Deploy' }} viewType="linkOnly" />
      </DeckRuntimeContext.Provider>,
    );

  beforeEach(() => {
    router = new UIRouterReact();
    runtime = createDeckRuntime(router);
  });

  afterEach(() => {
    runtime.dispose();
    router.dispose();
  });

  it.each([
    { executionId: undefined, stageId: 'stage-id' },
    { executionId: 'execution-id', stageId: undefined },
  ])('does not fetch or expose local changes when pipeline IDs are incomplete', async (ids) => {
    const getExecution = vi
      .spyOn(runtime.services.executionService, 'getExecution')
      .mockResolvedValue({ stages: [] } as IExecution);
    const localChanges = { commits: [commit('local-commit')] };
    const view = renderLink(localChanges);
    expect(await screen.findByText('View Changes')).toBeVisible();

    rerenderLink(view, {
      ...localChanges,
      metadata: metadata({ executionType: 'pipeline', ...ids }),
    });
    await act(async () => Promise.resolve());

    expect(getExecution).not.toHaveBeenCalled();
    expect(screen.queryByText('View Changes')).not.toBeInTheDocument();
  });

  it('clears pipeline-hydrated changes when metadata changes execution type', async () => {
    const pipelineCommit = commit('pipeline-commit');
    const getExecution = vi
      .spyOn(runtime.services.executionService, 'getExecution')
      .mockResolvedValue({ stages: [{ id: 'stage-id', context: { commits: [pipelineCommit] } }] } as IExecution);
    const pipelineMetadata = metadata({
      executionType: 'pipeline',
      executionId: 'execution-id',
      stageId: 'stage-id',
    });
    const view = renderLink({ metadata: pipelineMetadata });
    expect(await screen.findByText('View Changes')).toBeVisible();

    rerenderLink(view, {
      metadata: metadata({
        executionType: 'orchestration',
        executionId: 'execution-id',
        stageId: 'stage-id',
      }),
    });

    await waitFor(() => expect(screen.queryByText('View Changes')).not.toBeInTheDocument());
    expect(getExecution).toHaveBeenCalledTimes(1);
  });

  it('hydrates matching pipeline stage changes and renders them through the public UI', async () => {
    const user = setupUser();
    const pipelineCommit = commit('pipeline-commit');
    const getExecution = vi.spyOn(runtime.services.executionService, 'getExecution').mockResolvedValue({
      stages: [
        { id: 'other-stage', context: { commits: [commit('other-commit')] } },
        { id: 'stage-id', context: { commits: [pipelineCommit] } },
      ],
    } as IExecution);

    renderLink({
      commits: [commit('local-commit')],
      metadata: metadata({ executionType: 'pipeline', executionId: 'execution-id', stageId: 'stage-id' }),
    });
    await user.click(await screen.findByText('View Changes'));

    expect(getExecution).toHaveBeenCalledWith('execution-id');
    expect(await screen.findByText('Changes to Deploy')).toBeVisible();
    expect(screen.getByRole('link', { name: 'pipeline-commit' })).toHaveAttribute('href', pipelineCommit.commitUrl);
    expect(screen.queryByText('local-commit')).not.toBeInTheDocument();
    expect(screen.queryByText('other-commit')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(screen.queryByText('Changes to Deploy')).not.toBeInTheDocument());
  });
});
