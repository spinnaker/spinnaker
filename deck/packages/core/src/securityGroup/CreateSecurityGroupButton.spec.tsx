import { mount } from 'enzyme';
import React from 'react';

import type { IQService, IRootScopeService } from 'angular';
import { mock } from 'angular';

import { CloudProviderRegistry, ProviderSelectionService } from '../cloudProvider';
import { DeckRuntimeContext } from '../bootstrap/DeckRuntimeContext';
import { SETTINGS } from '../config/settings';
import { CreateSecurityGroupButton } from './CreateSecurityGroupButton';

describe('<CreateSecurityGroupButton />', () => {
  const runtimeServices = {} as any;
  let $q: IQService;
  let $rootScope: IRootScopeService;

  beforeEach(
    mock.inject((_$q_: IQService, _$rootScope_: IRootScopeService) => {
      $q = _$q_;
      $rootScope = _$rootScope_;
    }),
  );

  beforeEach(() => {
    SETTINGS.providers.buttonTestProvider = {
      defaults: {
        account: 'button-test-account',
        region: 'dev',
      },
    };
  });

  afterEach(SETTINGS.resetToOriginal);

  it('opens a React security group modal after selecting a provider from a React click handler', async () => {
    const modal = { show: jasmine.createSpy('show') };
    const app = {
      defaultCredentials: {},
      defaultRegions: {},
    } as any;
    spyOn(ProviderSelectionService, 'selectProvider').and.returnValue($q.when('buttonTestProvider'));
    spyOn(CloudProviderRegistry, 'getValue').and.returnValue({
      CreateSecurityGroupModal: modal,
    });

    mount(
      <DeckRuntimeContext.Provider value={{ services: runtimeServices }}>
        <CreateSecurityGroupButton app={app} />
      </DeckRuntimeContext.Provider>,
    )
      .find('button')
      .simulate('click');
    await settle();
    $rootScope.$digest();

    expect(modal.show).toHaveBeenCalledWith(
      {
        application: app,
        credentials: 'button-test-account',
        isNew: true,
        region: 'dev',
      },
      runtimeServices,
    );
  });

  it('opens a React security group modal for providers without configured defaults', async () => {
    const modal = { show: jasmine.createSpy('show') };
    const app = {
      defaultCredentials: {},
      defaultRegions: {},
    } as any;
    SETTINGS.providers.kubernetes = {};
    spyOn(ProviderSelectionService, 'selectProvider').and.returnValue($q.when('kubernetes'));
    spyOn(CloudProviderRegistry, 'getValue').and.returnValue({
      CreateSecurityGroupModal: modal,
    });

    mount(
      <DeckRuntimeContext.Provider value={{ services: runtimeServices }}>
        <CreateSecurityGroupButton app={app} />
      </DeckRuntimeContext.Provider>,
    )
      .find('button')
      .simulate('click');
    await settle();
    $rootScope.$digest();

    expect(modal.show).toHaveBeenCalledWith(
      {
        application: app,
        credentials: undefined,
        isNew: true,
        region: undefined,
      },
      runtimeServices,
    );
  });
});

const settle = () => new Promise((resolve) => setTimeout(resolve));
