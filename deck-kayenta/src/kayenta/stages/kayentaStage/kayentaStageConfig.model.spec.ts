import type { IKayentaStage } from 'kayenta/domain';

import type { DeckRuntimeServices } from '@spinnaker/core';

import type { IKayentaCloneServerGroupModal, IKayentaServerGroupModalDependencies } from './kayentaStageConfig.model';
import { addPair, editServerGroup } from './kayentaStageConfig.model';

describe('kayentaStageConfig server group modals', () => {
  it('passes runtime services to the React clone modal when adding a pair', async () => {
    const stage = createStage({ deployments: { baseline: {}, serverGroupPairs: [] } });
    const deps = createServerGroupModalDependencies({ selectedProvider: 'gce' });

    await addPair(stage, deps);

    expect(deps.providerSelectionService.selectProvider).toHaveBeenCalledWith(
      deps.application,
      'serverGroup',
      expect.any(Function),
    );
    const filterFn = (deps.providerSelectionService.selectProvider as jest.Mock).mock.calls[0][2];
    expect(filterFn(null, null, { serverGroup: { CloneServerGroupModal: {} } })).toBe(true);
    expect(filterFn(null, null, { serverGroup: {} })).toBe(false);

    const cloneServerGroupModal = getCloneServerGroupModal(deps);
    expect(cloneServerGroupModal.show).toHaveBeenCalledWith(
      expect.objectContaining({
        application: deps.application,
        command: expect.any(Object),
        title: 'Add Baseline + Canary Pair',
      }),
      deps.runtimeServices,
    );
    expect(deps).not.toHaveProperty('$uibModal');
  });

  it('passes runtime services to the React clone modal when editing a pair', async () => {
    const control = { application: 'fnord', cloudProvider: 'gce', freeFormDetails: 'baseline' };
    const stage = createStage({
      deployments: { baseline: { cloudProvider: 'gce' }, serverGroupPairs: [{ control, experiment: {} }] },
    });
    const deps = createServerGroupModalDependencies({ selectedProvider: 'gce' });

    await editServerGroup(stage, deps, control, 0, 'control');

    const cloneServerGroupModal = getCloneServerGroupModal(deps);
    expect(cloneServerGroupModal.show).toHaveBeenCalledWith(
      expect.objectContaining({
        application: deps.application,
        command: expect.any(Object),
        title: 'Configure Control Server Group',
      }),
      deps.runtimeServices,
    );
    expect(deps).not.toHaveProperty('$uibModal');
  });

  it('rejects clearly when a provider has no React clone server group modal', async () => {
    const stage = createStage({ deployments: { baseline: { cloudProvider: 'oracle' }, serverGroupPairs: [] } });
    const deps = createServerGroupModalDependencies({ selectedProvider: 'gce', registeredModal: null });

    await expect(addPair(stage, deps)).rejects.toThrow(
      'No React clone server group modal is registered for provider "oracle".',
    );
    expect(deps.serverGroupCommandBuilder.buildNewServerGroupCommandForPipeline).not.toHaveBeenCalled();
  });
});

function createStage(overrides: Partial<IKayentaStage> & { deployments?: any } = {}): IKayentaStage {
  return {
    canaryConfig: { scopes: [] },
    isNew: true,
    ...overrides,
  } as IKayentaStage;
}

function createServerGroupModalDependencies({
  selectedProvider,
  registeredModal = { show: jest.fn((props) => Promise.resolve(props.command)) },
}: {
  selectedProvider: string;
  registeredModal?: IKayentaCloneServerGroupModal | null;
}): IKayentaServerGroupModalDependencies {
  const command = { viewState: {}, strategy: 'redblack' };
  return {
    application: { name: 'fnord' },
    cloudProviderRegistry: {
      getValue: jest.fn(() => ({ CloneServerGroupModal: registeredModal })),
    },
    providerSelectionService: {
      selectProvider: jest.fn(() => Promise.resolve(selectedProvider)),
    },
    serverGroupCommandBuilder: {
      buildNewServerGroupCommandForPipeline: jest.fn(() => Promise.resolve(command)),
      buildServerGroupCommandFromPipeline: jest.fn(() => Promise.resolve(command)),
    },
    serverGroupTransformer: {
      convertServerGroupCommandToDeployConfiguration: jest.fn(() => ({ application: 'fnord', freeFormDetails: '' })),
    },
    runtimeServices: {} as DeckRuntimeServices,
  };
}

function getCloneServerGroupModal(deps: IKayentaServerGroupModalDependencies): IKayentaCloneServerGroupModal {
  const modal = deps.cloudProviderRegistry.getValue('gce', 'serverGroup').CloneServerGroupModal;
  if (!modal) {
    throw new Error('Expected a registered clone server group modal.');
  }
  return modal;
}
