import { mount } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';

import { DeckRuntimeContext } from '../../../../bootstrap/DeckRuntimeContext';
import type { IExecutionStage } from '../../../../domain';
import { ViewChangesLink } from '../../../../diffs/ViewChangesLink';
import { ServerGroupReader } from '../../../../serverGroup/serverGroupReader.service';
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

const createStage = (id: string, serverGroup: string): IExecutionStage =>
  (({
    id,
    name: 'Deploy',
    context: {
      account: 'test',
      application: 'app',
      buildInfo: { ancestor: '10', target: '11' },
      commits: [{ id: 'commit' }],
      'deploy.server.groups': { 'us-east-1': [serverGroup] },
      source: { region: 'us-east-1' },
    },
  } as any) as IExecutionStage);

const createProps = (stage: IExecutionStage) =>
  ({
    application: {} as any,
    current: 'changes',
    execution: {} as any,
    name: 'changes',
    stage,
  } as any);

describe('DeployChangesExecutionDetails', () => {
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) => (
    <DeckRuntimeContext.Provider value={{ services: { executionService: {} } } as any}>
      {children}
    </DeckRuntimeContext.Provider>
  );
  const mountDetails = (component: React.ReactElement) => mount(component, { wrappingComponent: RuntimeWrapper });

  it('merges Jenkins metadata from the source server group into the changes config', async () => {
    const sourceServerGroup = deferred<any>();
    const getServerGroup = spyOn(ServerGroupReader, 'getServerGroup').and.returnValue(sourceServerGroup.promise);
    const stage = createStage('stage-1', 'app-v001');
    const wrapper = mountDetails(<DeployChangesExecutionDetails {...createProps(stage)} />);

    expect(getServerGroup).toHaveBeenCalledWith('app', 'test', 'us-east-1', 'app-v001');

    await act(async () =>
      sourceServerGroup.resolve({ buildInfo: { jenkins: { host: 'https://jenkins/', name: 'job', number: '11' } } }),
    );
    wrapper.update();

    expect(wrapper.find(ViewChangesLink).prop('changeConfig')).toEqual({
      buildInfo: {
        ancestor: '10',
        target: '11',
        jenkins: { host: 'https://jenkins/', name: 'job', number: '11' },
      },
      commits: stage.context.commits,
      jarDiffs: undefined,
    });
    wrapper.unmount();
  });

  it('ignores source metadata loaded for a previous stage', async () => {
    const firstRequest = deferred<any>();
    const secondRequest = deferred<any>();
    spyOn(ServerGroupReader, 'getServerGroup').and.returnValues(firstRequest.promise, secondRequest.promise);
    const firstStage = createStage('stage-1', 'app-v001');
    const secondStage = createStage('stage-2', 'app-v002');
    secondStage.context.buildInfo = { ancestor: '20', target: '21' };
    const wrapper = mountDetails(<DeployChangesExecutionDetails {...createProps(firstStage)} />);

    wrapper.setProps(createProps(secondStage));
    await act(async () =>
      firstRequest.resolve({ buildInfo: { jenkins: { host: 'https://stale/', name: 'job', number: '11' } } }),
    );
    wrapper.update();

    expect(wrapper.find(ViewChangesLink).prop('changeConfig').buildInfo).toEqual({ ancestor: '20', target: '21' });

    await act(async () =>
      secondRequest.resolve({ buildInfo: { jenkins: { host: 'https://current/', name: 'job', number: '21' } } }),
    );
    wrapper.update();

    expect(wrapper.find(ViewChangesLink).prop('changeConfig').buildInfo.jenkins.host).toBe('https://current/');
    wrapper.unmount();
  });

  it('refreshes change data and source metadata when execution hydration retains the stage reference', async () => {
    const firstRequest = deferred<any>();
    const secondRequest = deferred<any>();
    const getServerGroup = spyOn(ServerGroupReader, 'getServerGroup').and.returnValues(
      firstRequest.promise,
      secondRequest.promise,
    );
    const stage = createStage('stage-1', 'app-v001');
    const wrapper = mountDetails(<DeployChangesExecutionDetails {...createProps(stage)} />);

    stage.context = {
      ...stage.context,
      account: 'updated-account',
      buildInfo: { ancestor: '20', target: '21' },
      commits: [{ id: 'updated-commit' }],
      jarDiffs: { updated: [{ name: 'library' }] },
      'deploy.server.groups': { 'eu-west-1': ['app-v002'] },
      source: { region: 'eu-west-1' },
    };
    stage.outputs = { refreshed: true };
    wrapper.setProps(createProps(stage));

    expect(wrapper.find(ViewChangesLink).prop('changeConfig')).toEqual({
      buildInfo: { ancestor: '20', target: '21' },
      commits: stage.context.commits,
      jarDiffs: stage.context.jarDiffs,
    });
    expect(getServerGroup).toHaveBeenCalledTimes(2);
    expect(getServerGroup).toHaveBeenCalledWith('app', 'updated-account', 'eu-west-1', 'app-v002');

    await act(async () =>
      firstRequest.resolve({ buildInfo: { jenkins: { host: 'https://stale/', name: 'job', number: '11' } } }),
    );
    wrapper.update();
    expect(wrapper.find(ViewChangesLink).prop('changeConfig').buildInfo.jenkins).toBeUndefined();

    await act(async () =>
      secondRequest.resolve({ buildInfo: { jenkins: { host: 'https://current/', name: 'job', number: '21' } } }),
    );
    wrapper.update();
    expect(wrapper.find(ViewChangesLink).prop('changeConfig').buildInfo.jenkins.host).toBe('https://current/');
    wrapper.unmount();
  });

  it('ignores source metadata after unmounting', async () => {
    const sourceServerGroup = deferred<any>();
    spyOn(ServerGroupReader, 'getServerGroup').and.returnValue(sourceServerGroup.promise);
    const consoleError = spyOn(console, 'error');
    const wrapper = mountDetails(
      <DeployChangesExecutionDetails {...createProps(createStage('stage-1', 'app-v001'))} />,
    );

    wrapper.unmount();
    await act(async () =>
      sourceServerGroup.resolve({ buildInfo: { jenkins: { host: 'https://jenkins/', name: 'job', number: '11' } } }),
    );

    expect(consoleError).not.toHaveBeenCalled();
  });
});
