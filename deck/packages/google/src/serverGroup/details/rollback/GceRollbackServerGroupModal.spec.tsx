import { shallow } from 'enzyme';
import React from 'react';

import { PlatformHealthOverride, TaskReason, UserVerification } from '@spinnaker/core';

import {
  buildGceRollbackJob,
  GceRollbackServerGroupModal,
  getGceRollbackCandidates,
} from './GceRollbackServerGroupModal';

describe('GceRollbackServerGroupModal', () => {
  const application = { name: 'fnord' } as any;
  const serverGroup = {
    account: 'prod',
    app: 'fnord',
    cluster: 'fnord-main',
    name: 'fnord-main-v004',
    region: 'us-central1',
  } as any;

  it('keeps only disabled candidates in the same application, cluster, account, and region', () => {
    const eligible = {
      account: 'prod',
      app: 'fnord',
      cluster: 'fnord-main',
      isDisabled: true,
      name: 'fnord-main-v003',
      region: 'us-central1',
    };
    const candidates = [
      eligible,
      { ...eligible, app: 'other', name: 'other-main-v003' },
      { ...eligible, cluster: 'fnord-test', name: 'fnord-test-v003' },
      { ...eligible, account: 'staging', name: 'fnord-main-v003-staging' },
      { ...eligible, region: 'europe-west1', name: 'fnord-main-v003-eu' },
      { ...eligible, isDisabled: false, name: 'fnord-main-v002' },
    ] as any[];

    expect(getGceRollbackCandidates(application, serverGroup, candidates)).toEqual([eligible]);
  });

  it('sorts rollback candidates newest first', () => {
    const candidate = {
      account: 'prod',
      app: 'fnord',
      cluster: 'fnord-main',
      isDisabled: true,
      region: 'us-central1',
    };

    const result = getGceRollbackCandidates(application, serverGroup, [
      { ...candidate, name: 'fnord-main-v001' },
      { ...candidate, name: 'fnord-main-v003' },
      { ...candidate, name: 'fnord-main-v002' },
    ] as any[]);

    expect(result.map(({ name }) => name)).toEqual(['fnord-main-v003', 'fnord-main-v002', 'fnord-main-v001']);
  });

  it('builds the exact explicit rollback job with reason and health override', () => {
    const job = buildGceRollbackJob({ attributes: { platformHealthOnlyShowOverride: true } } as any, serverGroup, {
      interestingHealthProviderNames: ['Google'],
      reason: 'bad release',
      restoreServerGroupName: 'fnord-main-v003',
    });

    expect(job).toEqual({
      interestingHealthProviderNames: ['Google'],
      platformHealthOnlyShowOverride: true,
      reason: 'bad release',
      rollbackContext: {
        restoreServerGroupName: 'fnord-main-v003',
        rollbackServerGroupName: 'fnord-main-v004',
      },
      rollbackType: 'EXPLICIT',
    });
  });

  it('renders reason, account verification, and the Google platform-health override', () => {
    const wrapper = shallow(
      <GceRollbackServerGroupModal
        application={
          {
            attributes: { platformHealthOnly: true, platformHealthOnlyShowOverride: true },
            name: 'fnord',
          } as any
        }
        dismissModal={jasmine.createSpy('dismissModal')}
        serverGroup={serverGroup}
        serverGroups={[]}
        serverGroupWriter={{ rollbackServerGroup: jasmine.createSpy('rollbackServerGroup') } as any}
      />,
    );

    expect(wrapper.find(TaskReason).exists()).toBe(true);
    expect(wrapper.find(UserVerification).prop('account')).toBe('prod');
    expect(wrapper.find(PlatformHealthOverride).props()).toEqual(
      jasmine.objectContaining({
        interestingHealthProviderNames: ['Google'],
        platformHealthType: 'Google',
      }),
    );
  });

  it('submits the exact rollback writer job only after account verification', () => {
    const applicationWithHealthOverride = {
      attributes: { platformHealthOnly: true, platformHealthOnlyShowOverride: true },
      name: 'fnord',
      serverGroups: { refresh: jasmine.createSpy('refresh') },
    } as any;
    const candidate = {
      account: 'prod',
      app: 'fnord',
      cluster: 'fnord-main',
      isDisabled: true,
      name: 'fnord-main-v003',
      region: 'us-central1',
    } as any;
    const rollbackServerGroup = jasmine.createSpy('rollbackServerGroup').and.returnValue(Promise.resolve({}));
    const wrapper = shallow(
      <GceRollbackServerGroupModal
        application={applicationWithHealthOverride}
        dismissModal={jasmine.createSpy('dismissModal')}
        serverGroup={serverGroup}
        serverGroups={[candidate]}
        serverGroupWriter={{ rollbackServerGroup }}
      />,
    );
    const monitor = (wrapper.state() as any).taskMonitor;
    spyOn(monitor, 'submit').and.callFake((submitMethod: () => PromiseLike<any>) => submitMethod());

    wrapper.find('select').simulate('change', { target: { value: candidate.name } });
    wrapper.find(TaskReason).prop('onChange')('bad release');
    expect(wrapper.find('button.btn-primary').prop('disabled')).toBe(true);

    wrapper.find(UserVerification).prop('onValidChange')(true);
    wrapper.find('button.btn-primary').simulate('click');

    expect(rollbackServerGroup).toHaveBeenCalledWith(serverGroup, applicationWithHealthOverride, {
      interestingHealthProviderNames: ['Google'],
      platformHealthOnlyShowOverride: true,
      reason: 'bad release',
      rollbackContext: {
        restoreServerGroupName: 'fnord-main-v003',
        rollbackServerGroupName: 'fnord-main-v004',
      },
      rollbackType: 'EXPLICIT',
    });
  });

  it('dismisses the modal from the task monitor', () => {
    const dismissModal = jasmine.createSpy('dismissModal');
    const wrapper = shallow(
      <GceRollbackServerGroupModal
        application={{ name: 'fnord', serverGroups: { refresh: jasmine.createSpy('refresh') } } as any}
        dismissModal={dismissModal}
        serverGroup={serverGroup}
        serverGroups={[]}
        serverGroupWriter={{ rollbackServerGroup: jasmine.createSpy('rollbackServerGroup') } as any}
      />,
    );

    (wrapper.state() as any).taskMonitor.closeModal();

    expect(dismissModal).toHaveBeenCalled();
  });
});
