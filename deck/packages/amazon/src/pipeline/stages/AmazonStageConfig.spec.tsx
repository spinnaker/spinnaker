import { mount } from 'enzyme';
import React from 'react';
import { AccountService } from '@spinnaker/core';

import { AmazonStageConfig, getAmazonStageFields } from './AmazonStageConfig';

describe('AmazonStageConfig', () => {
  beforeEach(() => {
    spyOn(AccountService, 'listAccounts').and.returnValue(Promise.resolve([{ name: 'test', type: 'aws' }] as any));
    spyOn(AccountService, 'getUniqueAttributeForAllAccounts').and.returnValue(Promise.resolve(['us-east-1']) as any);
    spyOn(AccountService, 'getAllAccountDetailsForProvider').and.returnValue(Promise.resolve([]) as any);
  });

  function renderStage(stage: any = {}) {
    const application = {
      defaultCredentials: {},
      defaultRegions: {},
      getDataSource: () => ({ data: [] }),
    };

    return mount(
      <AmazonStageConfig
        application={application as any}
        pipeline={{} as any}
        stage={{ type: 'enableServerGroup', cloudProviderType: 'aws', ...stage }}
        updateStageField={jasmine.createSpy('updateStageField') as any}
      />,
    );
  }

  it('defines generic fields only for simple scalar stages', () => {
    const simpleStageTypes = [
      'bake',
      'cloneServerGroup',
      'destroyAsg',
      'destroyServerGroup',
      'disableAsg',
      'disableCluster',
      'disableServerGroup',
      'enableAsg',
      'enableServerGroup',
      'findAmi',
      'findImage',
      'rollbackCluster',
      'scaleDownCluster',
      'shrinkCluster',
    ];
    const dedicatedStageTypes = [
      'deployCloudFormation',
      'findImageFromTags',
      'modifyAwsScalingProcess',
      'modifyScalingProcess',
      'resizeAsg',
      'resizeServerGroup',
      'upsertImageTags',
    ];
    const fallbackFields = getAmazonStageFields({ type: 'unknown' });
    const genericStageTypes = [...simpleStageTypes, ...dedicatedStageTypes].filter(
      (type) => getAmazonStageFields({ type }) !== fallbackFields,
    );

    expect(genericStageTypes).toEqual(simpleStageTypes);
  });

  it('renders account as a selector for target server group stages', () => {
    const wrapper = renderStage({ credentials: 'test' });

    expect(wrapper.find('select[name="credentials"]').exists()).toBe(true);
    expect(wrapper.find('input[name="credentials"]').exists()).toBe(false);
  });

  it('keeps server group target as a selector', () => {
    const wrapper = renderStage({ target: 'current_asg' });

    expect(
      wrapper
        .find('select')
        .filterWhere((node) => node.find('option[value="current_asg"]').exists())
        .exists(),
    ).toBe(true);
  });

  it('does not update account and region state after unmount', async () => {
    let resolveAccounts: (accounts: any[]) => void;
    let resolveRegions: (regions: string[]) => void;
    (AccountService.listAccounts as jasmine.Spy).and.returnValue(
      new Promise((resolve) => {
        resolveAccounts = resolve;
      }) as any,
    );
    (AccountService.getUniqueAttributeForAllAccounts as jasmine.Spy).and.returnValue(
      new Promise((resolve) => {
        resolveRegions = resolve;
      }) as any,
    );
    spyOn(console, 'error');

    const wrapper = renderStage();
    wrapper.unmount();
    resolveAccounts!([{ name: 'test', type: 'aws' }]);
    resolveRegions!(['us-east-1']);
    await Promise.resolve();

    expect((console.error as jasmine.Spy).calls.allArgs().join('\n')).not.toContain(
      "Can't perform a React state update on an unmounted component",
    );
  });
});
