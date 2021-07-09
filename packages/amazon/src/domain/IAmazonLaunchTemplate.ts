import { IBlockDeviceMapping } from './IAmazonBlockDeviceMapping';

export interface IIamInstanceProfile {
  arn?: string;
  name: string;
}

export interface ICpuOptions {
  coreCount?: number;
  threadsPerCore?: number;
}

export interface IElasticGpuSpecification {
  type?: string;
}

export interface IElasticInterfaceAccelerator {
  count?: number;
  type?: string;
}

export interface ISpotMarketOptions {
  blockDurationMinutes?: number;
  instanceInterruptionBehavior?: string;
  maxPrice?: string;
  spotInstanceType?: 'one-time' | 'persistent';
  validUntil?: string;
}

export interface IInstanceMarketOptions {
  marketType?: string;
  spotOptions?: ISpotMarketOptions;
}

export interface ILicenseConfig {
  licenseConfigurationArn?: string;
}

export interface IMetadataOptions {
  httpEndpoint?: 'disabled' | 'enabled';
  httpPutResponseHopLimit?: number;
  httpsTokens?: 'required' | 'optional';
  state?: 'pending' | 'applied';
}

export interface INetworkInterface {
  deviceIndex: number;
  groups: string[];
  ipv6AddressCount?: number;
  associatePublicIpAddress?: boolean;
  ipv6Addresses?: string[];
}

export interface ITagSpecification {
  resourceType?: string;
  tagSet?: Array<{
    key: string;
    value: string;
  }>;
}

export interface ICreditSpecification {
  cpuCredits?: string;
}

export interface ILaunchTemplateData {
  [attribute: string]: any;
  blockDeviceMappings?: IBlockDeviceMapping[];
  cpuOptions?: ICpuOptions;
  creditSpecification?: ICreditSpecification;
  disableApiTermination?: boolean;
  ebsOptimized: boolean;
  elasticGpuSpecifications?: IElasticGpuSpecification[];
  elasticInferenceAccelerators?: IElasticInterfaceAccelerator[];
  iamInstanceProfile: IIamInstanceProfile;
  imageId: string;
  instanceInitiatedShutdownBehavior?: 'stop' | 'terminate';
  instanceMarketOptions?: IInstanceMarketOptions;
  instanceType: string;
  kernelId?: string;
  keyName: string;
  licenseSpecifications?: ILicenseConfig[];
  metadataOptions: IMetadataOptions;
  monitoring: {
    enabled: boolean;
  };
  networkInterfaces?: INetworkInterface[];
  ramDiskId?: string;
  securityGroupIds: string[];
  securityGroups: string[];
  tagSpecifications?: ITagSpecification[];
  userData?: string;
}

export interface IAmazonLaunchTemplate {
  createdBy: string;
  createdTime: number;
  defaultVersion: boolean;
  launchTemplateData: ILaunchTemplateData;
  launchTemplateId: string;
  launchTemplateName: string;
  versionDescription?: string;
  versionNumber: number;
}
