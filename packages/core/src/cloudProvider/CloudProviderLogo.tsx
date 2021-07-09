import React from 'react';

import { CloudProviderRegistry } from './CloudProviderRegistry';
import { Tooltip } from '../presentation/Tooltip';

import './cloudProviderLogo.less';

export interface ICloudProviderLogoProps {
  provider: string;
  height: string;
  width: string;
  showTooltip?: boolean;
}

export interface ICloudProviderLogoState {
  tooltip?: string;
  logo: React.ComponentType<React.SVGProps<HTMLOrSVGElement>>;
}

export const CloudProviderLogo = ({ height, provider, showTooltip, width }: ICloudProviderLogoProps) => {
  const [tooltip, setTooltip] = React.useState<string>(undefined);
  const RegistryLogo = CloudProviderRegistry.getValue(provider, 'cloudProviderLogo');

  React.useEffect(() => {
    if (showTooltip) {
      setTooltip(CloudProviderRegistry.getValue(provider, 'name') || provider);
    }
  }, [showTooltip]);

  const ProviderLogo = RegistryLogo ? (
    <RegistryLogo height={height} width={width} />
  ) : (
    <span className={`icon icon-${provider}`} style={{ height, width }} />
  );

  if (tooltip) {
    return <Tooltip value={tooltip}>{ProviderLogo}</Tooltip>;
  }

  return <span className="cloud-provider-logo">{ProviderLogo}</span>;
};
