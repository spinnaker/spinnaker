import React from 'react';
import { shallow } from 'enzyme';
import {
  createCustomMockLaunchTemplate,
  mockLaunchTemplate,
  mockLaunchTemplateData,
  mockServerGroup,
} from '@spinnaker/mocks';
import { Application, ApplicationModelBuilder } from '@spinnaker/core';
import { IAmazonServerGroupView, IScalingPolicy } from '../../../domain';
import { LaunchTemplateDetailsSection } from './LaunchTemplateDetailsSection';

describe('Launch template details', () => {
  let app: Application;
  beforeEach(() => {
    app = ApplicationModelBuilder.createApplicationForTests('testapp');
  });

  const baseServerGroup = {
    ...mockServerGroup,
    launchTemplate: createCustomMockLaunchTemplate('test123', {
      ...mockLaunchTemplateData,
      kernelId: 'kernal-abc',
      ramDiskId: 'ramDisk-123',
      instanceMarketOptions: {
        spotOptions: {
          maxPrice: '0.50',
        },
      },
    }),
    image: {
      description: 'ancestor_name=testBaseImage',
      imageLocation: 'location',
    },
    scalingPolicies: [] as IScalingPolicy[],
  } as IAmazonServerGroupView;

  it('should not render if no launch template', () => {
    const testServerGroup = {
      ...mockServerGroup,
      scalingPolicies: [] as IScalingPolicy[],
    } as IAmazonServerGroupView;

    const wrapper = shallow(<LaunchTemplateDetailsSection serverGroup={testServerGroup} app={app} />);
    expect(wrapper.type()).toEqual(null);
  });

  it('should render base info', () => {
    const wrapper = shallow(<LaunchTemplateDetailsSection serverGroup={baseServerGroup} app={app} />);
    const labeledValues = wrapper.find('LabeledValue');

    expect(labeledValues.length).toEqual(12);
    expect(labeledValues.at(0).prop('value')).toEqual('test123');
    expect(labeledValues.at(1).prop('value')).toEqual('ami-0123456789');
    expect(labeledValues.at(4).prop('value')).toEqual('m5.large');
    expect(labeledValues.at(5).prop('value')).toEqual('testapplicationInstanceProfile');
    expect(labeledValues.at(6).prop('value')).toEqual('disabled');
  });

  it('should conditionally render image information', () => {
    const wrapper = shallow(<LaunchTemplateDetailsSection serverGroup={baseServerGroup} app={app} />);
    const labeledValues = wrapper.find('LabeledValue');

    expect(labeledValues.at(2).prop('label')).toEqual('Image Name');
    expect(labeledValues.at(3).prop('label')).toEqual('Base Image Name');

    const newServerGroup = {
      ...baseServerGroup,
    };
    delete newServerGroup.image;
    wrapper.setProps({
      serverGroup: newServerGroup,
    });

    const imageName = wrapper.findWhere((lv) => lv.prop('label') === 'Image Name');
    const baseImage = wrapper.findWhere((lv) => lv.prop('label') === 'Base Image Name');

    expect(imageName.length).toEqual(0);
    expect(baseImage.length).toEqual(0);
  });

  it('should conditionally render additional info', () => {
    const wrapper = shallow(<LaunchTemplateDetailsSection serverGroup={baseServerGroup} app={app} />);
    const labeledValues = wrapper.find('LabeledValue');

    expect(labeledValues.at(7).prop('label')).toEqual('Max Spot Price');
    expect(labeledValues.at(8).prop('label')).toEqual('Key Name');
    expect(labeledValues.at(9).prop('label')).toEqual('Kernel ID');
    expect(labeledValues.at(10).prop('label')).toEqual('Ramdisk ID');
    expect(labeledValues.at(11).prop('label')).toEqual('User Data');

    const newServerGroup = {
      ...baseServerGroup,
      launchTemplate: mockLaunchTemplate,
    };
    delete newServerGroup.launchTemplate.launchTemplateData.userData;
    delete newServerGroup.launchTemplate.launchTemplateData.keyName;

    wrapper.setProps({
      serverGroup: newServerGroup,
    });

    const spotPrice = wrapper.findWhere((lv) => lv.prop('label') === 'Max Spot Price');
    const keyName = wrapper.findWhere((lv) => lv.prop('label') === 'Key Name');
    const kernelId = wrapper.findWhere((lv) => lv.prop('label') === 'Kernel ID');
    const ramdiskId = wrapper.findWhere((lv) => lv.prop('label') === 'Ramdisk ID');
    const userData = wrapper.findWhere((lv) => lv.prop('label') === 'User Data');

    expect(spotPrice.length).toEqual(0);
    expect(keyName.length).toEqual(0);
    expect(ramdiskId.length).toEqual(0);
    expect(kernelId.length).toEqual(0);
    expect(userData.length).toEqual(0);
  });
});
