import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import type { IManifestDeploymentOptionsProps, ITrafficManagementConfig } from './ManifestDeploymentOptions';
import { defaultTrafficManagementConfig, ManifestDeploymentOptions } from './ManifestDeploymentOptions';
import { ManifestKindSearchService } from '../../../manifest/ManifestKindSearch';

describe('<ManifestDeploymentOptions />', () => {
  const onConfigChangeSpy = vi.fn();
  let props: IManifestDeploymentOptionsProps;

  const buildConfig = (strategy: string = null): ITrafficManagementConfig => ({
    enabled: true,
    options: {
      namespace: null,
      services: [],
      enableTraffic: false,
      strategy,
    },
  });

  beforeEach(() => {
    vi.spyOn(ManifestKindSearchService, 'search').mockResolvedValue([]);
    props = {
      accounts: [],
      config: {
        ...defaultTrafficManagementConfig,
        options: { ...defaultTrafficManagementConfig.options },
      },
      onConfigChange: onConfigChangeSpy,
      selectedAccount: null,
    };
  });

  describe('view', () => {
    it('renders only the enable checkbox when config is disabled', () => {
      render(<ManifestDeploymentOptions {...props} />);

      expect(screen.getAllByRole('checkbox')).toHaveLength(1);
      expect(screen.queryByText('Service(s) Namespace', { selector: '.label-text' })).not.toBeInTheDocument();
    });

    it('renders config fields for namespace, services, traffic, and strategy when config is enabled', () => {
      props.config = buildConfig();
      render(<ManifestDeploymentOptions {...props} />);

      ['Enable', 'Service(s) Namespace', 'Service(s)', 'Traffic', 'Strategy'].forEach((label) => {
        expect(screen.getByText(label, { selector: '.label-text' })).toBeInTheDocument();
      });
    });
  });

  describe('functionality', () => {
    it('updates config.enabled when the enable checkbox is toggled', () => {
      render(<ManifestDeploymentOptions {...props} />);

      fireEvent.click(screen.getByRole('checkbox', { name: /Spinnaker manages traffic/ }));

      expect(onConfigChangeSpy).toHaveBeenCalledWith({
        ...defaultTrafficManagementConfig,
        options: { ...defaultTrafficManagementConfig.options },
        enabled: true,
      });
    });

    it('disables the traffic checkbox when a non-None rollout strategy is selected', () => {
      props.config = buildConfig('redblack');
      render(<ManifestDeploymentOptions {...props} />);

      expect(screen.getByRole('checkbox', { name: 'Send client requests to new pods' })).toBeDisabled();
    });

    it('disables the traffic checkbox when blue/green rollout strategy is selected', () => {
      props.config = buildConfig('bluegreen');
      render(<ManifestDeploymentOptions {...props} />);

      expect(screen.getByRole('checkbox', { name: 'Send client requests to new pods' })).toBeDisabled();
    });

    it('strategy bluegreen does not display a warning', () => {
      props.config = buildConfig('bluegreen');
      render(<ManifestDeploymentOptions {...props} />);

      expect(screen.queryByText(/Red\/black strategy is deprecated/)).not.toBeInTheDocument();
    });

    it('strategy highlander does not display a warning', () => {
      props.config = buildConfig('highlander');
      render(<ManifestDeploymentOptions {...props} />);

      expect(screen.queryByText(/Red\/black strategy is deprecated/)).not.toBeInTheDocument();
    });

    it('strategy redblack displays a warning', () => {
      props.config = buildConfig('redblack');
      render(<ManifestDeploymentOptions {...props} />);

      expect(screen.getByText(/Red\/black strategy is deprecated/)).toBeInTheDocument();
    });
  });
});
