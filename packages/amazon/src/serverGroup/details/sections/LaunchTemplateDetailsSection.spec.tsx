import React from 'react';
import { shallow } from 'enzyme';
import {
  createCustomMockLaunchTemplate,
  mockLaunchTemplate,
  mockLaunchTemplateData,
  mockServerGroup,
} from '@spinnaker/mocks';
import { Application, ApplicationModelBuilder } from '@spinnaker/core';
import { IAmazonServerGroupView, IAmazonMixedInstancesPolicy, IScalingPolicy } from '../../../domain';
import { LaunchTemplateDetailsSection } from './LaunchTemplateDetailsSection';
import { MultipleInstanceTypesSubSection } from './MultipleInstanceTypesSubSection';

describe('Launch template details', () => {
  let app: Application;
  beforeEach(() => {
    app = ApplicationModelBuilder.createApplicationForTests('testapp');
  });

  const baseServerGroupWithLt = {
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

  const baseServerGroupWithMipOverrides = {
    ...mockServerGroup,
    image: {
      description: 'ancestor_name=testBaseImage',
      imageLocation: 'location',
    },
    scalingPolicies: [] as IScalingPolicy[],
    mixedInstancesPolicy: {
      allowedInstanceTypes: ['some.type.medium', 'some.type.large'],
      instancesDiversification: {
        onDemandAllocationStrategy: 'prioritized',
        onDemandBaseCapacity: 1,
        onDemandPercentageAboveBaseCapacity: 50,
        spotAllocationStrategy: 'capacity-optimized',
        spotMaxPrice: '1.5',
      },
      launchTemplates: [
        {
          createdBy: 'testuser@test.com',
          createdTime: 1588787656527,
          defaultVersion: true,
          launchTemplateData: mockLaunchTemplateData,
          launchTemplateId: '123456',
          launchTemplateName: 'testLaunchTemplatev001',
          versionDescription: 'Test purposes',
          versionNumber: 1,
        },
      ],
      launchTemplateOverridesForInstanceType: [
        {
          instanceType: 'some.type.medium',
          weightedCapacity: '2',
        },
        {
          instanceType: 'some.type.large',
          weightedCapacity: '4',
        },
      ],
    } as IAmazonMixedInstancesPolicy,
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
    const wrapper = shallow(<LaunchTemplateDetailsSection serverGroup={baseServerGroupWithLt} app={app} />);
    const labeledValues = wrapper.find('LabeledValue');

    expect(labeledValues.length).toEqual(12);
    expect(labeledValues.at(0).prop('value')).toEqual('test123');
    expect(labeledValues.at(1).prop('value')).toEqual('ami-0123456789');
    expect(labeledValues.at(4).prop('value')).toEqual('m5.large');
    expect(labeledValues.at(5).prop('value')).toEqual('testapplicationInstanceProfile');
    expect(labeledValues.at(6).prop('value')).toEqual('disabled');
  });

  it('should render launch template details for server group with launchTemplate', () => {
    const testServerGroup = {
      ...mockServerGroup,
      launchTemplate: createCustomMockLaunchTemplate('ltWithCredits', {
        ...mockLaunchTemplateData,
        instanceMarketOptions: {
          spotOptions: {
            maxPrice: '0.50',
          },
        },
        creditSpecification: {
          cpuCredits: 'unlimited',
        },
        keyName: 'test',
        kernelId: 'kernal-abc',
        ramDiskId: 'ramDisk-123',
        userData: 'thisisfakeuserdata',
      }),
      image: {
        description: 'ancestor_name=testBaseImage',
        imageLocation: 'location',
      },
    } as IAmazonServerGroupView;

    const wrapper = shallow(<LaunchTemplateDetailsSection serverGroup={testServerGroup} app={app} />);
    const actualLabeledValues = wrapper.find('LabeledValue');

    expect(actualLabeledValues.length).toEqual(13);
    const expectedLabels = new Map([
      ['Name', 'ltWithCredits'],
      ['Image ID', 'ami-0123456789'],
      ['Image Name', 'location'],
      ['Base Image Name', 'testBaseImage'],
      ['Instance Type', 'm5.large'],
      ['CPU Credit Specification', 'unlimited'],
      ['IAM Profile', 'testapplicationInstanceProfile'],
      ['Instance Monitoring', 'disabled'],
      ['Max Spot Price', '0.50'],
      ['Key Name', 'test'],
      ['Kernel ID', 'kernal-abc'],
      ['Ramdisk ID', 'ramDisk-123'],
      ['User Data', ''],
    ]);
    let index = 0;
    expectedLabels.forEach((value, key) => {
      let labeledValue = actualLabeledValues.at(index++);
      expect(labeledValue.prop('label')).toEqual(key);
      value != '' && expect(labeledValue.prop('value')).toEqual(value);
    });
  });

  it('should conditionally render launch template details for server group with mixedInstancesPolicy', () => {
    const testServerGroup = baseServerGroupWithMipOverrides;

    const wrapper = shallow(<LaunchTemplateDetailsSection serverGroup={testServerGroup} app={app} />);
    const labeledValues = wrapper.find('LabeledValue');

    expect(labeledValues.length).toEqual(9);
    expect(labeledValues.at(0).prop('value')).toEqual('testLaunchTemplatev001');
    expect(labeledValues.at(1).prop('value')).toEqual('ami-0123456789');
    expect(labeledValues.at(2).prop('value')).toEqual('location');
    expect(labeledValues.at(3).prop('value')).toEqual('testBaseImage');
    expect(labeledValues.at(4).prop('value')).toEqual('testapplicationInstanceProfile');
    expect(labeledValues.at(5).prop('value')).toEqual('disabled');
    expect(labeledValues.at(6).prop('value')).toEqual('1.5');
    expect(labeledValues.at(7).prop('value')).toEqual('test');
    expect(labeledValues.at(8).prop('label')).toEqual('User Data');

    const multipleInstanceTypes = shallow(
      <MultipleInstanceTypesSubSection
        instanceTypeOverrides={testServerGroup.mixedInstancesPolicy.launchTemplateOverridesForInstanceType}
      />,
    );
    expect(multipleInstanceTypes.isEmptyRender()).toEqual(false);
    const multipleInstanceTypesTableRows = multipleInstanceTypes.find('td');

    expect(multipleInstanceTypesTableRows.length).toEqual(4);
    expect(multipleInstanceTypesTableRows.at(0).text()).toEqual('some.type.medium');
    expect(multipleInstanceTypesTableRows.at(1).text()).toEqual('2');
    expect(multipleInstanceTypesTableRows.at(2).text()).toEqual('some.type.large');
    expect(multipleInstanceTypesTableRows.at(3).text()).toEqual('4');
  });

  it('should conditionally render image information', () => {
    const wrapper = shallow(<LaunchTemplateDetailsSection serverGroup={baseServerGroupWithLt} app={app} />);
    const labeledValues = wrapper.find('LabeledValue');

    expect(labeledValues.at(2).prop('label')).toEqual('Image Name');
    expect(labeledValues.at(3).prop('label')).toEqual('Base Image Name');

    const newServerGroup = {
      ...baseServerGroupWithLt,
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
    const wrapper = shallow(<LaunchTemplateDetailsSection serverGroup={baseServerGroupWithLt} app={app} />);
    const labeledValues = wrapper.find('LabeledValue');

    expect(labeledValues.at(7).prop('label')).toEqual('Max Spot Price');
    expect(labeledValues.at(8).prop('label')).toEqual('Key Name');
    expect(labeledValues.at(9).prop('label')).toEqual('Kernel ID');
    expect(labeledValues.at(10).prop('label')).toEqual('Ramdisk ID');
    expect(labeledValues.at(11).prop('label')).toEqual('User Data');

    const newServerGroup = {
      ...baseServerGroupWithLt,
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

  it('should not render multiple instance types subsection when overrides are not specified', () => {
    let testServerGroup = baseServerGroupWithMipOverrides;
    testServerGroup.mixedInstancesPolicy.launchTemplateOverridesForInstanceType = null;

    const multipleInstanceTypes = shallow(
      <MultipleInstanceTypesSubSection
        instanceTypeOverrides={testServerGroup.mixedInstancesPolicy.launchTemplateOverridesForInstanceType}
      />,
    );
    expect(multipleInstanceTypes.isEmptyRender()).toEqual(true);
  });
});
