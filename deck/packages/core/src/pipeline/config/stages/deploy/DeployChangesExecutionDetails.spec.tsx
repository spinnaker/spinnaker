import { UIRouterReact } from '@uirouter/react';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import { ApplicationModelBuilder } from '../../../../application/applicationModel.builder';
import { createDeckRuntime } from '../../../../bootstrap/DeckRuntime';
import type { DeckRuntime } from '../../../../bootstrap/DeckRuntime';
import { DeckRuntimeContext } from '../../../../bootstrap/DeckRuntimeContext';
import type { IExecution, IExecutionStage, IJenkinsInfo, IServerGroup } from '../../../../domain';
import { ServerGroupReader } from '../../../../serverGroup/serverGroupReader.service';
import type { IExecutionDetailsSectionProps } from '../common';
import { DeployChangesExecutionDetails } from './DeployExecutionDetails';

interface IDeferred<T> {
  promise: Promise<T>;
  resolve: (value: T) => void;
}

function deferred<T>(): IDeferred<T> {
  let resolve: (value: T) => void;
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

const commit = (id: string) => ({
  authorDisplayName: 'Developer',
  commitUrl: `https://example.com/commits/${id}`,
  displayId: id,
  id,
  message: `Commit ${id}`,
  timestamp: 0,
});

const createStage = (id: string, serverGroup: string): IExecutionStage =>
  ({
    id,
    name: 'Deploy',
    context: {
      account: 'test',
      application: 'app',
      buildInfo: { ancestor: '10', target: '11' },
      commits: [commit('commit')],
      'deploy.server.groups': { 'us-east-1': [serverGroup] },
      source: { region: 'us-east-1' },
    },
  } as IExecutionStage);

const serverGroupWithJenkins = (jenkins: IJenkinsInfo): IServerGroup => ({ buildInfo: { jenkins } } as IServerGroup);

describe('DeployChangesExecutionDetails', () => {
  let runtime: DeckRuntime;
  let router: UIRouterReact;

  const createProps = (stage: IExecutionStage): IExecutionDetailsSectionProps => ({
    application: ApplicationModelBuilder.createApplicationForTests('app'),
    current: 'changes',
    execution: { application: 'app', id: 'execution-id', stages: [] } as IExecution,
    name: 'changes',
    stage,
  });

  const renderDetails = (stage: IExecutionStage) =>
    render(
      <DeckRuntimeContext.Provider value={runtime}>
        <DeployChangesExecutionDetails {...createProps(stage)} />
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

  it('renders stage change data through ViewChangesLink', async () => {
    const user = userEvent.setup();
    const jenkins = { host: 'https://jenkins.example.com/', name: 'deploy-app', number: '11' };
    vi.spyOn(ServerGroupReader, 'getServerGroup').mockResolvedValue(serverGroupWithJenkins(jenkins));

    renderDetails(createStage('stage-1', 'app-v001'));
    await user.click(await screen.findByText('View Changes'));

    expect(await screen.findByText('Changes to Deploy')).toBeVisible();
    expect(screen.getByRole('link', { name: 'Build: #10' })).toHaveAttribute(
      'href',
      'https://jenkins.example.com/job/deploy-app/10',
    );
    expect(screen.getByRole('link', { name: 'Build: #11' })).toHaveAttribute(
      'href',
      'https://jenkins.example.com/job/deploy-app/11',
    );
    await user.click(screen.getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(screen.queryByText('Changes to Deploy')).not.toBeInTheDocument());
  });

  it('renders the current source response and does not let a stale response overwrite it', async () => {
    const user = userEvent.setup();
    const staleRequest = deferred<IServerGroup>();
    const currentRequest = deferred<IServerGroup>();
    const getServerGroup = vi
      .spyOn(ServerGroupReader, 'getServerGroup')
      .mockReturnValueOnce(staleRequest.promise)
      .mockReturnValueOnce(currentRequest.promise);
    const stage = createStage('stage-1', 'app-v001');
    const view = renderDetails(stage);

    stage.context = {
      ...stage.context,
      account: 'updated-account',
      buildInfo: { ancestor: '20', target: '21' },
      commits: [commit('updated-commit')],
      jarDiffs: { updated: [{ name: 'library' }] },
      'deploy.server.groups': { 'eu-west-1': ['app-v002'] },
      source: { region: 'eu-west-1' },
    };
    stage.outputs = { refreshed: true };
    view.rerender(
      <DeckRuntimeContext.Provider value={runtime}>
        <DeployChangesExecutionDetails {...createProps(stage)} />
      </DeckRuntimeContext.Provider>,
    );

    expect(getServerGroup).toHaveBeenNthCalledWith(1, 'app', 'test', 'us-east-1', 'app-v001');
    expect(getServerGroup).toHaveBeenNthCalledWith(2, 'app', 'updated-account', 'eu-west-1', 'app-v002');

    const currentJenkins = { host: 'https://current.jenkins.example.com/', name: 'deploy-app', number: '21' };
    await act(async () => currentRequest.resolve(serverGroupWithJenkins(currentJenkins)));
    await user.click(await screen.findByText('View Changes'));
    expect(screen.getByRole('link', { name: 'Build: #21' })).toHaveAttribute(
      'href',
      'https://current.jenkins.example.com/job/deploy-app/21',
    );
    await user.click(screen.getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(screen.queryByText('Changes to Deploy')).not.toBeInTheDocument());

    const staleJenkins = { host: 'https://stale.jenkins.example.com/', name: 'deploy-app', number: '11' };
    await act(async () => staleRequest.resolve(serverGroupWithJenkins(staleJenkins)));
    await user.click(screen.getByText('View Changes'));

    expect(screen.getByRole('link', { name: 'Build: #21' })).toHaveAttribute(
      'href',
      'https://current.jenkins.example.com/job/deploy-app/21',
    );
    expect(screen.queryByText('https://stale.jenkins.example.com/')).not.toBeInTheDocument();
  });

  it('refreshes source metadata when execution hydration retains the stage reference', () => {
    const firstRequest = deferred<IServerGroup>();
    const secondRequest = deferred<IServerGroup>();
    const getServerGroup = vi
      .spyOn(ServerGroupReader, 'getServerGroup')
      .mockReturnValueOnce(firstRequest.promise)
      .mockReturnValueOnce(secondRequest.promise);
    const stage = createStage('stage-1', 'app-v001');
    const view = renderDetails(stage);

    stage.context = {
      ...stage.context,
      account: 'updated-account',
      'deploy.server.groups': { 'eu-west-1': ['app-v002'] },
      source: { region: 'eu-west-1' },
    };
    stage.outputs = { refreshed: true };
    view.rerender(
      <DeckRuntimeContext.Provider value={runtime}>
        <DeployChangesExecutionDetails {...createProps(stage)} />
      </DeckRuntimeContext.Provider>,
    );

    expect(getServerGroup).toHaveBeenCalledTimes(2);
    expect(getServerGroup).toHaveBeenLastCalledWith('app', 'updated-account', 'eu-west-1', 'app-v002');
  });

  it('ignores source metadata after unmounting', async () => {
    const sourceServerGroup = deferred<IServerGroup>();
    vi.spyOn(ServerGroupReader, 'getServerGroup').mockReturnValue(sourceServerGroup.promise);
    const consoleError = vi.spyOn(console, 'error').mockReturnValue(undefined);
    const view = renderDetails(createStage('stage-1', 'app-v001'));

    view.unmount();
    sourceServerGroup.resolve(
      serverGroupWithJenkins({ host: 'https://jenkins.example.com/', name: 'deploy-app', number: '11' }),
    );
    await sourceServerGroup.promise;

    expect(consoleError).not.toHaveBeenCalled();
  });
});
