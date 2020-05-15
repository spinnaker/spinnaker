import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IClassicLaunchWhitelist {
  region: string;
  credentials: string;
}

export interface ITencentCloudProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    region?: string;
    subnetType?: string;
    vpc?: string;
  };
  defaultSecurityGroups?: string[];
  loadBalancers?: {
    inferInternalFlagFromSubnet: boolean;
    certificateTypes?: string[];
  };
  classicLaunchLockout?: number;
  classicLaunchWhitelist?: IClassicLaunchWhitelist[];
  metrics?: {
    customNamespaces?: string[];
  };
  minRootVolumeSize?: number;
  disableSpotPricing?: boolean;
}

export const TencentCloudProviderSettings: ITencentCloudProviderSettings = (SETTINGS.providers
  .tencentcloud as ITencentCloudProviderSettings) || { defaults: {} };

if (TencentCloudProviderSettings) {
  TencentCloudProviderSettings.resetToOriginal = SETTINGS.resetProvider('tencentcloud');
}
