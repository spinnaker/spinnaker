import type {
  IAmazonLaunchTemplate,
  IIamInstanceProfile,
  ILaunchTemplateData,
  IMetadataOptions,
} from '@spinnaker/amazon';

import { createMockBlockDeviceMapping } from './mockAmazonBlockDeviceMapping';

export const mockIamInstanceProfile: IIamInstanceProfile = {
  name: 'testapplicationInstanceProfile',
};

export const mockMetadataOptions: IMetadataOptions = {
  httpEndpoint: 'enabled',
  httpTokens: 'required',
};

export const mockLaunchTemplateData: ILaunchTemplateData = {
  ebsOptimized: true,
  iamInstanceProfile: mockIamInstanceProfile,
  imageId: 'ami-0123456789',
  instanceType: 'm5.large',
  keyName: 'test',
  metadataOptions: mockMetadataOptions,
  monitoring: {
    enabled: false,
  },
  networkInterfaces: [],
  blockDeviceMappings: [createMockBlockDeviceMapping()],
  securityGroupIds: ['sg-1', 'sg-2'],
  securityGroups: [],
  tagSpecifications: [],
  userData: 'thisisfakeuserdata',
};

export const mockLaunchTemplate: IAmazonLaunchTemplate = {
  createdBy: 'testuser@test.com',
  createdTime: 1588787656527,
  defaultVersion: true,
  launchTemplateData: mockLaunchTemplateData,
  launchTemplateId: '123456',
  launchTemplateName: 'testLaunchTemplatev001',
  versionDescription: 'Test purposes',
  versionNumber: 1,
};

export const createCustomMockLaunchTemplate = (name: string, data: ILaunchTemplateData): IAmazonLaunchTemplate => ({
  ...mockLaunchTemplate,
  launchTemplateName: name,
  launchTemplateData: {
    ...mockLaunchTemplateData,
    ...data,
  },
});
