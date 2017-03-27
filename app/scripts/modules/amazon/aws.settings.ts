import { IProviderSettings, SETTINGS } from 'core/config/settings';

interface IClassicLaunchWhitelist {
  region: string;
  credentials: string;
}

export interface IAWSProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    region?: string;
    iamRole?: string;
    subnetType?: string;
  };
  defaultSecurityGroups?: string[];
  loadBalancers?: {
    inferInternalFlagFromSubnet: boolean;
    certificateTypes?: string[];
  };
  useAmiBlockDeviceMappings?: boolean;
  classicLaunchLockout?: number;
  classicLaunchWhitelist?: IClassicLaunchWhitelist[];
  metrics?: {
    customNamespaces?: string[];
  };
}

export const AWSProviderSettings: IAWSProviderSettings = <IAWSProviderSettings>SETTINGS.providers.aws || { defaults: {} };
if (AWSProviderSettings) {
  AWSProviderSettings.resetToOriginal = SETTINGS.resetToOriginal;
}
