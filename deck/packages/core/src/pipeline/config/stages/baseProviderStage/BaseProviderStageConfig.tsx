import React from 'react';

import { CloudProviderLabel, CloudProviderLogo } from '../../../../cloudProvider';
import { StageConfigField } from '../common/stageConfigField/StageConfigField';
import { ReactSelectInput } from '../../../../presentation';

export interface IBaseProviderStageConfigProps {
  providers: string[];
  selectedProvider?: string;
  readOnly: boolean;
  onProviderChange(provider: string): void;
}

const Provider = ({ provider }: { provider: string }) => (
  <span>
    <CloudProviderLogo provider={provider} height="14px" width="14px" />{' '}
    <span className="base-provider-label">
      <CloudProviderLabel provider={provider} />
    </span>
  </span>
);

export function BaseProviderStageConfig({
  providers,
  selectedProvider,
  readOnly,
  onProviderChange,
}: IBaseProviderStageConfigProps) {
  React.useEffect(() => {
    if (!readOnly && providers.length === 1 && !selectedProvider) {
      onProviderChange(providers[0]);
    }
  }, [onProviderChange, providers, readOnly, selectedProvider]);

  if (!providers.length) {
    return null;
  }

  const displayedProvider = selectedProvider || (providers.length === 1 ? providers[0] : undefined);

  return (
    <StageConfigField label="Provider">
      {readOnly || providers.length === 1 ? (
        displayedProvider && (
          <p className="form-control-static">
            <Provider provider={displayedProvider} />
          </p>
        )
      ) : (
        <ReactSelectInput
          clearable={false}
          name="cloudProviderType"
          onChange={(event) => onProviderChange(event.target.value)}
          optionRenderer={(option: any) => <Provider provider={option.value} />}
          options={providers.map((provider) => ({ label: provider, value: provider }))}
          placeholder="Select a provider"
          value={selectedProvider || ''}
          valueRenderer={(option: any) => <Provider provider={option.value} />}
        />
      )}
    </StageConfigField>
  );
}
