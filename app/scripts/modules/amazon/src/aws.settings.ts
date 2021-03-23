import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IClassicLaunchAllowlist {
  region: string;
  credentials: string;
}

export interface IAWSProviderSettings extends IProviderSettings {
  bakeWarning?: string;
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
    // Enables IPv6 as an advanced setting
    enableIPv6?: boolean;
    // If `enableIPv6` is true, will automatically opt asgs in the test environment into IPv6
    setIPv6InTest?: boolean;
    enableIMDSv2?: boolean;
    defaultIMDSv2AppAgeLimit?: number;
    enableCpuCredits?: boolean;
    recommendedSubnets?: string[];
    subnetWarning?: string;
  };
  useAmiBlockDeviceMappings?: boolean;
}

export const AWSProviderSettings: IAWSProviderSettings = (SETTINGS.providers.aws as IAWSProviderSettings) || {
  defaults: {},
};
if (AWSProviderSettings) {
  AWSProviderSettings.resetToOriginal = SETTINGS.resetProvider('aws');
}
