import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IClassicLaunchAllowlist {
  region: string;
  credentials: string;
}

export interface IAWSProviderSettings extends IProviderSettings {
  classicLaunchLockout?: number;
  classicLaunchAllowlist?: IClassicLaunchAllowlist[];
  createLoadBalancerWarnings?: {
    application?: string;
    classic?: string;
    network?: string;
  };
  crossAccountIngressExclusions?: {
    [credentials: string]: string | string[];
  };
  securityGroupExclusions?: string[];
  defaults: {
    account?: string;
    iamRole?: string;
    region?: string;
    subnetType?: string;
    vpc?: string;
  };
  defaultSecurityGroups?: string[];
  disableSpotPricing?: boolean;
  instanceTypes?: {
    exclude?: {
      categories?: string[];
      families?: string[];
    };
  };
  loadBalancers?: {
    certificateTypes?: string[];
    disableManualOidcDialog?: boolean;
    inferInternalFlagFromSubnet: boolean;
  };
  metrics?: {
    customNamespaces?: string[];
  };
  minRootVolumeSize?: number;
  serverGroups?: {
    enableLaunchTemplates?: boolean;
    enableIPv6?: boolean;
    enableIMDSv2?: boolean;
    defaultIMDSv2AppAgeLimit?: number;
  };
  useAmiBlockDeviceMappings?: boolean;
}

export const AWSProviderSettings: IAWSProviderSettings = (SETTINGS.providers.aws as IAWSProviderSettings) || {
  defaults: {},
};
if (AWSProviderSettings) {
  AWSProviderSettings.resetToOriginal = SETTINGS.resetProvider('aws');
}
