import { mount } from 'enzyme';
import React from 'react';

import {
  CloudProviderRegistry,
  ConfirmationModalService,
  DeckRuntimeContext,
  ServerGroupReader,
  ServerGroupWarningMessageService,
} from '@spinnaker/core';

import { AzureImageReader } from '../../image/image.reader';
import { registerAzureProvider } from '../../azure.module';
import { AzureServerGroupCommandBuilder } from '../configure/serverGroupCommandBuilder.service';
import { AzureCloneServerGroupModal } from '../configure/wizard/AzureCloneServerGroupModal';
import {
  AzureServerGroupActions as RoutedAzureServerGroupActions,
  AzureServerGroupActionsComponent as AzureServerGroupActions,
  azureServerGroupDetailsGetter,
  azureServerGroupDetailsSections,
} from './azureServerGroupDetails';

describe('Azure server group details', () => {
  let runtimeServices: any;
  const stateService = { go: jasmine.createSpy('go'), includes: jasmine.createSpy('includes').and.returnValue(true) };
  const routerProps = { router: {} as any, stateParams: {}, stateService: stateService as any };
  const serverGroupParams = {
    name: 'azure-v001',
    accountId: 'test-account',
    region: 'westus',
  };

  function buildApp(overrides: any = {}): any {
    return {
      name: 'fnord',
      serverGroups: { data: [], refresh: jasmine.createSpy('refresh') },
      loadBalancers: { data: [] },
      securityGroups: { data: [] },
      clusters: [],
      ready: () => Promise.resolve(),
      ...overrides,
    };
  }

  function subscribeToGetter(props: any, autoClose = jasmine.createSpy('autoClose')): Promise<any> {
    return new Promise((resolve, reject) => {
      azureServerGroupDetailsGetter(props, autoClose).subscribe({
        next: resolve,
        error: reject,
      });
    });
  }

  function mountActions(app: any, serverGroup: any) {
    runtimeServices = {
      serverGroupCommandBuilder: new AzureServerGroupCommandBuilder(Promise),
      serverGroupWriter: {
        destroyServerGroup: jasmine.createSpy('destroyServerGroup'),
        disableServerGroup: jasmine.createSpy('disableServerGroup'),
        enableServerGroup: jasmine.createSpy('enableServerGroup'),
      },
    };
    return mount(
      <DeckRuntimeContext.Provider value={{ services: runtimeServices }}>
        <AzureServerGroupActions {...routerProps} app={app} serverGroup={serverGroup} />
      </DeckRuntimeContext.Provider>,
    );
  }

  beforeEach(() => {
    spyOn(ServerGroupReader, 'getServerGroup').and.returnValue(
      Promise.resolve({
        name: 'azure-v001',
        account: 'test-account',
        region: 'westus',
        image: { imageName: 'ubuntu' },
        launchConfig: { securityGroups: ['sg-1'] },
      }) as any,
    );
  });

  it('finds summaries in application server groups, fetches details, and merges summary fields', async () => {
    const summary = {
      name: 'azure-v001',
      account: 'test-account',
      region: 'westus',
      type: 'azure',
      cluster: 'azure',
      createdTime: 123,
      instanceCounts: { total: 1, up: 1 },
      instances: [{ id: 'i-1' }],
    };
    const app = buildApp({ serverGroups: { data: [summary] } });

    const result = await subscribeToGetter({ app, serverGroup: serverGroupParams });

    expect(ServerGroupReader.getServerGroup).toHaveBeenCalledWith('fnord', 'test-account', 'westus', 'azure-v001');
    expect(result).toEqual(jasmine.objectContaining(summary));
    expect(result.account).toBe('test-account');
    expect(result.image.imageName).toBe('ubuntu');
  });

  it('falls back to summaries on matching load balancers', async () => {
    const loadBalancerSummary = {
      name: 'azure-v001',
      account: 'test-account',
      region: 'westus',
      cluster: 'azure',
      loadBalancers: ['lb-1'],
    };
    const app = buildApp({
      loadBalancers: {
        data: [
          { account: 'other-account', region: 'westus', serverGroups: [{ name: 'azure-v001' }] },
          { account: 'test-account', region: 'westus', serverGroups: [loadBalancerSummary] },
        ],
      },
    });

    const result = await subscribeToGetter({ app, serverGroup: serverGroupParams });

    expect(result).toEqual(jasmine.objectContaining(loadBalancerSummary));
  });

  it('auto-closes when the summary is missing', async () => {
    const autoClose = jasmine.createSpy('autoClose');
    const app = buildApp();

    await new Promise<void>((resolve) => {
      azureServerGroupDetailsGetter({ app, serverGroup: serverGroupParams }, autoClose).subscribe({
        next: () => fail('should not emit a server group'),
        complete: resolve,
      });
    });

    expect(autoClose).toHaveBeenCalled();
    expect(ServerGroupReader.getServerGroup).not.toHaveBeenCalled();
  });

  it('auto-closes when server group details are missing', async () => {
    (ServerGroupReader.getServerGroup as jasmine.Spy).and.returnValue(Promise.resolve(null) as any);
    const autoClose = jasmine.createSpy('autoClose');
    const app = buildApp({ serverGroups: { data: [{ ...serverGroupParams, account: 'test-account' }] } });

    await new Promise<void>((resolve) => {
      azureServerGroupDetailsGetter({ app, serverGroup: serverGroupParams }, autoClose).subscribe({
        next: () => fail('should not emit a server group'),
        complete: resolve,
      });
    });

    expect(autoClose).toHaveBeenCalled();
  });

  it('registers React server group details without Angular fallback keys', () => {
    registerAzureProvider();

    expect(CloudProviderRegistry.getValue('azure', 'serverGroup.detailsGetter')).toBe(azureServerGroupDetailsGetter);
    expect(CloudProviderRegistry.getValue('azure', 'serverGroup.detailsActions').render).toBe(
      (RoutedAzureServerGroupActions as any).render,
    );
    expect(CloudProviderRegistry.getValue('azure', 'serverGroup.detailsSections')).toEqual(
      azureServerGroupDetailsSections,
    );
    expect(CloudProviderRegistry.getValue('azure', 'serverGroup.detailsController')).toBeNull();
    expect(CloudProviderRegistry.getValue('azure', 'serverGroup.detailsTemplateUrl')).toBeNull();
  });

  it('wires destroy, disable, and enable actions to confirmation modals', () => {
    const app = buildApp();
    const serverGroup = { name: 'azure-v001', account: 'test-account', region: 'westus', isDisabled: false };
    spyOn(ConfirmationModalService, 'confirm');
    spyOn(ServerGroupWarningMessageService, 'addDestroyWarningMessage');
    spyOn(ServerGroupWarningMessageService, 'addDisableWarningMessage');

    const wrapper = mountActions(app, serverGroup);
    wrapper
      .find('a')
      .filterWhere((node) => node.text() === 'Destroy')
      .simulate('click');
    wrapper
      .find('a')
      .filterWhere((node) => node.text() === 'Disable')
      .simulate('click');
    wrapper
      .find('a')
      .filterWhere((node) => node.text() === 'Enable')
      .simulate('click');

    expect(ServerGroupWarningMessageService.addDestroyWarningMessage).toHaveBeenCalled();
    expect(ServerGroupWarningMessageService.addDisableWarningMessage).toHaveBeenCalled();
    expect(
      (ConfirmationModalService.confirm as jasmine.Spy).calls.allArgs().map(([params]) => params.buttonText),
    ).toEqual(['Destroy azure-v001', 'Disable azure-v001', 'Enable azure-v001']);
    (ConfirmationModalService.confirm as jasmine.Spy).calls.first().args[0].taskMonitorConfig.onTaskComplete();
    expect(stateService.go).toHaveBeenCalledWith('^');
  });

  it('builds a clone command before opening the clone wizard', async () => {
    registerAzureProvider();
    const app = buildApp();
    const serverGroup = { name: 'azure-v001', account: 'test-account', region: 'westus' };
    const command = { viewState: { mode: 'clone' }, source: { serverGroupName: 'azure-v001' } };
    spyOn(AzureServerGroupCommandBuilder.prototype as any, 'buildServerGroupCommandFromExisting').and.returnValue(
      Promise.resolve(command),
    );
    spyOn(AzureCloneServerGroupModal, 'show').and.returnValue(Promise.resolve());

    const wrapper = mountActions(app, serverGroup);
    wrapper
      .find('a')
      .filterWhere((node) => node.text() === 'Clone')
      .simulate('click');
    await Promise.resolve();

    expect(AzureServerGroupCommandBuilder.prototype.buildServerGroupCommandFromExisting).toHaveBeenCalledWith(
      app,
      serverGroup,
    );
    expect(AzureCloneServerGroupModal.show).toHaveBeenCalledWith(
      {
        application: app,
        command,
        title: 'Clone azure-v001',
      },
      runtimeServices,
    );
  });

  it('opens the clone wizard with image data seeded on the built command', async () => {
    registerAzureProvider();
    const images = [{ imageName: 'ubuntu-west', amis: { westus: ['ami-west'] } }];
    const app = buildApp();
    const serverGroup = {
      name: 'azure-v001',
      account: 'test-account',
      region: 'westus',
      image: { imageName: 'ubuntu-west' },
      loadBalancers: [],
      selectedVnetSubnets: [],
      selectedVnet: { name: 'vnet-a' },
      securityGroups: [],
      zones: [],
      dataDisks: [],
      sku: { name: 'Standard_DS1_v2', capacity: 1 },
      capacity: { min: 1, max: 1, desired: 1 },
    };
    spyOn(AzureImageReader.prototype, 'findImages').and.returnValue(Promise.resolve(images));
    spyOn(AzureCloneServerGroupModal, 'show').and.returnValue(Promise.resolve());

    const wrapper = mountActions(app, serverGroup);
    wrapper
      .find('a')
      .filterWhere((node) => node.text() === 'Clone')
      .simulate('click');
    await Promise.resolve();
    await Promise.resolve();

    const props = (AzureCloneServerGroupModal.show as jasmine.Spy).calls.mostRecent().args[0];
    expect(props.command.images).toBe(images);
    expect(props.command.imageName).toBe('ubuntu-west');
    expect(props.command.selectedImage).toBe(images[0]);
  });
});
