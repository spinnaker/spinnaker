import { mount as enzymeMount } from 'enzyme';
import React from 'react';
import Select from 'react-select';
import { of } from 'rxjs';

import { AccountService, DeckRuntimeContext, TaskMonitor } from '@spinnaker/core';

import { AwsModalFooter } from '../../../common/AwsModalFooter';

import {
  EditSecurityGroupsModal,
  filterSecurityGroupsForServerGroup,
  isLaunchTemplateBacked,
} from './EditSecurityGroupsModal';

describe('EditSecurityGroupsModal', () => {
  let originalAccounts: typeof AccountService.accounts$;
  let runtimeServices: any;
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) => (
    <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>{children}</DeckRuntimeContext.Provider>
  );
  const shallowModal = (props: any) =>
    enzymeMount(<EditSecurityGroupsModal {...props} />, { wrappingComponent: RuntimeWrapper });

  beforeEach(() => {
    originalAccounts = AccountService.accounts$;
    AccountService.accounts$ = of([]);
    runtimeServices = {};
  });

  afterEach(() => {
    AccountService.accounts$ = originalAccounts;
  });

  const serverGroup = {
    name: 'deck-main-v001',
    account: 'test',
    region: 'us-east-1',
    vpcId: 'vpc-1',
  } as any;

  const application = { name: 'deck', serverGroups: { refresh: jasmine.createSpy('refresh') } } as any;
  const modalProps = {
    application,
    serverGroup,
    closeModal: jasmine.createSpy('closeModal'),
    dismissModal: jasmine.createSpy('dismissModal'),
  };

  async function flush(): Promise<void> {
    await Promise.resolve();
    await Promise.resolve();
  }

  it('filters by account, region, and VPC while preserving unresolved attached groups', () => {
    const attached = [
      { id: 'sg-attached', name: 'attached' },
      { id: 'sg-unresolved', name: 'sg-unresolved' },
    ] as any;
    const allGroups = {
      test: {
        aws: {
          'us-east-1': [
            { id: 'sg-attached', name: 'attached', vpcId: 'vpc-1' },
            { id: 'sg-available', name: 'available', vpcId: 'vpc-1' },
            { id: 'sg-wrong-vpc', name: 'wrong-vpc', vpcId: 'vpc-2' },
          ],
          'eu-west-1': [{ id: 'sg-wrong-region', name: 'wrong-region', vpcId: 'vpc-1' }],
        },
      },
      other: { aws: { 'us-east-1': [{ id: 'sg-wrong-account', name: 'wrong-account', vpcId: 'vpc-1' }] } },
    } as any;

    expect(filterSecurityGroupsForServerGroup(allGroups, serverGroup, attached).map((group) => group.id)).toEqual([
      'sg-attached',
      'sg-unresolved',
      'sg-available',
    ]);
  });

  it('treats direct and mixed-instance launch templates as launch-template-backed', () => {
    expect(isLaunchTemplateBacked({ launchTemplate: {} } as any)).toBe(true);
    expect(isLaunchTemplateBacked({ mixedInstancesPolicy: {} } as any)).toBe(true);
    expect(isLaunchTemplateBacked({ launchConfig: {} } as any)).toBe(false);
  });

  it('shows a retryable error without losing selections and recovers after retry', async () => {
    const selected = [{ id: 'sg-attached', name: 'attached' }] as any;
    const allGroups = {
      test: { aws: { 'us-east-1': [{ id: 'sg-available', name: 'available', vpcId: 'vpc-1' }] } },
    };
    const getAllSecurityGroups = jasmine
      .createSpy('getAllSecurityGroups')
      .and.returnValues(Promise.reject(new Error('inventory unavailable')), Promise.resolve(allGroups));
    runtimeServices.securityGroupReader = { getAllSecurityGroups };
    spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({ result: Promise.resolve() } as any);

    const wrapper = shallowModal({ ...modalProps, securityGroups: selected });
    await flush();
    wrapper.update();

    expect(wrapper.find(Select).prop('isLoading')).toBe(false);
    expect(wrapper.find('.security-groups-load-error').text()).toContain('Unable to load');
    expect(wrapper.find(AwsModalFooter).prop('isValid')).toBe(false);
    expect(wrapper.state('securityGroups')).toEqual(selected);

    wrapper.find('.security-groups-load-error button').simulate('click');
    expect(wrapper.find(Select).prop('isLoading')).toBe(true);
    await flush();
    wrapper.update();

    expect(getAllSecurityGroups).toHaveBeenCalledTimes(2);
    expect(wrapper.find('.security-groups-load-error').length).toBe(0);
    expect(wrapper.find(AwsModalFooter).prop('isValid')).toBe(true);
    expect(wrapper.state('securityGroups')).toEqual(selected);
    expect((wrapper.state('availableSecurityGroups') as any[]).map((group) => group.id)).toEqual([
      'sg-attached',
      'sg-available',
    ]);
  });

  it('does not update state when security-group loading completes after unmount', async () => {
    let finishLoading: (groups: any) => void;
    const getAllSecurityGroups = jasmine.createSpy('getAllSecurityGroups').and.returnValue(
      new Promise((resolve) => {
        finishLoading = resolve;
      }),
    );
    runtimeServices.securityGroupReader = { getAllSecurityGroups };
    spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({ result: Promise.resolve() } as any);
    const wrapper = shallowModal(modalProps);
    const modal = wrapper.instance() as EditSecurityGroupsModal;
    const setState = spyOn(modal, 'setState').and.callThrough();

    wrapper.unmount();
    finishLoading({});
    await flush();

    expect(setState).not.toHaveBeenCalled();
  });

  it('submits selected groups through the writer with mixed-instance launch-template state', () => {
    spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({ result: Promise.resolve() } as any);
    const update = jasmine.createSpy('updateSecurityGroups').and.returnValue(Promise.resolve({} as any));
    const selected = [{ id: 'sg-attached', name: 'attached' }] as any;
    const modal = new EditSecurityGroupsModal({
      application,
      securityGroups: selected,
      serverGroup: { ...serverGroup, mixedInstancesPolicy: {} },
      closeModal: jasmine.createSpy('closeModal'),
      dismissModal: jasmine.createSpy('dismissModal'),
    } as any) as any;
    modal.context = { services: { serverGroupWriter: { updateSecurityGroups: update } } };
    modal.state.taskMonitor = { submit: (method: () => any) => method() };

    modal.submit();

    expect(update).toHaveBeenCalledWith(
      jasmine.objectContaining({ name: 'deck-main-v001' }),
      selected,
      application,
      true,
    );
  });
});
