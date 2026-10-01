import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import type { Application } from '@spinnaker/core';
import { CollapsibleSectionStateCache } from '@spinnaker/core';
import {
  createCustomMockLaunchTemplate,
  mockLaunchTemplate,
  mockLaunchTemplateData,
  mockServerGroup,
} from '@spinnaker/mocks';

import { LaunchTemplateDetailsSection } from './LaunchTemplateDetailsSection';
import { MultipleInstanceTypesSubSection } from './MultipleInstanceTypesSubSection';
import type { IAmazonMixedInstancesPolicy, IAmazonServerGroupView, IScalingPolicy } from '../../../domain';

describe('Launch template details', () => {
  const app = {} as Application;
  const expectValue = (label: string, value: string) => {
    const term = screen.getByText(label, { selector: 'dt' });
    expect(term.nextElementSibling).toHaveTextContent(value);
  };
  const expand = () => fireEvent.click(screen.getByText('Launch Template'));

  const baseServerGroupWithLt = {
    ...mockServerGroup,
    launchTemplate: createCustomMockLaunchTemplate('test123', {
      ...mockLaunchTemplateData,
      kernelId: 'kernal-abc',
      ramDiskId: 'ramDisk-123',
      userData: btoa('test user data'),
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
      instancesDistribution: {
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
          launchTemplateData: { ...mockLaunchTemplateData, userData: btoa('test user data') },
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

  beforeEach(() => vi.spyOn(CollapsibleSectionStateCache, 'isSet').mockReturnValue(false));

  it('should not render if no launch template', () => {
    const testServerGroup = {
      ...mockServerGroup,
      scalingPolicies: [] as IScalingPolicy[],
    } as IAmazonServerGroupView;

    const { container } = render(<LaunchTemplateDetailsSection serverGroup={testServerGroup} app={app} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('should render base info', () => {
    render(<LaunchTemplateDetailsSection serverGroup={baseServerGroupWithLt} app={app} />);
    expand();
    expectValue('Name', 'test123');
    expectValue('Image ID', 'ami-0123456789');
    expectValue('Instance Type', 'm5.large');
    expectValue('IAM Profile', 'testapplicationInstanceProfile');
    expectValue('Instance Monitoring', 'disabled');
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
        userData: btoa('test user data'),
      }),
      image: {
        description: 'ancestor_name=testBaseImage',
        imageLocation: 'location',
      },
    } as IAmazonServerGroupView;

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
    render(<LaunchTemplateDetailsSection serverGroup={testServerGroup} app={app} />);
    expand();
    expectedLabels.forEach((value, key) => value && expectValue(key, value));
    expect(screen.getByText('User Data', { selector: 'dt' })).toBeInTheDocument();
  });

  it('should conditionally render launch template details for server group with mixedInstancesPolicy', () => {
    const testServerGroup = baseServerGroupWithMipOverrides;

    render(<LaunchTemplateDetailsSection serverGroup={testServerGroup} app={app} />);
    expand();
    expectValue('Name', 'testLaunchTemplatev001');
    expectValue('Image ID', 'ami-0123456789');
    expectValue('Image Name', 'location');
    expectValue('Base Image Name', 'testBaseImage');
    expectValue('IAM Profile', 'testapplicationInstanceProfile');
    expectValue('Instance Monitoring', 'disabled');
    expectValue('Max Spot Price', '1.5');
    expect(screen.getByRole('cell', { name: 'some.type.medium' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: '2' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'some.type.large' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: '4' })).toBeInTheDocument();
  });

  it('should conditionally render image information', () => {
    const { rerender } = render(<LaunchTemplateDetailsSection serverGroup={baseServerGroupWithLt} app={app} />);
    expand();
    expect(screen.getByText('Image Name', { selector: 'dt' })).toBeInTheDocument();
    expect(screen.getByText('Base Image Name', { selector: 'dt' })).toBeInTheDocument();

    const newServerGroup = {
      ...baseServerGroupWithLt,
    };
    delete newServerGroup.image;
    rerender(<LaunchTemplateDetailsSection serverGroup={newServerGroup} app={app} />);
    expect(screen.queryByText('Image Name', { selector: 'dt' })).not.toBeInTheDocument();
    expect(screen.queryByText('Base Image Name', { selector: 'dt' })).not.toBeInTheDocument();
  });

  it('should conditionally render additional info', () => {
    const { rerender } = render(<LaunchTemplateDetailsSection serverGroup={baseServerGroupWithLt} app={app} />);
    expand();
    ['Max Spot Price', 'Key Name', 'Kernel ID', 'Ramdisk ID', 'User Data'].forEach((label) =>
      expect(screen.getByText(label, { selector: 'dt' })).toBeInTheDocument(),
    );

    const newServerGroup = {
      ...baseServerGroupWithLt,
      launchTemplate: mockLaunchTemplate,
    };
    delete newServerGroup.launchTemplate.launchTemplateData.userData;
    delete newServerGroup.launchTemplate.launchTemplateData.keyName;

    rerender(<LaunchTemplateDetailsSection serverGroup={newServerGroup} app={app} />);
    ['Max Spot Price', 'Key Name', 'Kernel ID', 'Ramdisk ID', 'User Data'].forEach((label) =>
      expect(screen.queryByText(label, { selector: 'dt' })).not.toBeInTheDocument(),
    );
  });

  it('should not render multiple instance types subsection when overrides are not specified', () => {
    const { container } = render(<MultipleInstanceTypesSubSection instanceTypeOverrides={null} />);
    expect(container).toBeEmptyDOMElement();
  });
});
