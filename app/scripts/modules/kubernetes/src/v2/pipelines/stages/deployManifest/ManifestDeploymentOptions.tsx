import { module } from 'angular';
import * as DOMPurify from 'dompurify';
import * as React from 'react';
import { react2angular } from 'react2angular';
import { cloneDeep, find, get, map, set, split } from 'lodash';
import Select, { Option } from 'react-select';

import { IAccountDetails, IDeploymentStrategy, StageConfigField } from '@spinnaker/core';

import { ManifestKindSearchService } from 'kubernetes/v2/manifest/ManifestKindSearch';
import { rolloutStrategies } from 'kubernetes/v2/rolloutStrategy';

export interface ITrafficManagementConfig {
  enabled: boolean;
  options: ITrafficManagementOptions;
}

export interface ITrafficManagementOptions {
  namespace: string;
  services: string[];
  enableTraffic: boolean;
  strategy: string;
}

export const defaultTrafficManagementConfig: ITrafficManagementConfig = {
  enabled: false,
  options: {
    namespace: null,
    services: [],
    enableTraffic: false,
    strategy: null,
  },
};

export interface IManifestDeploymentOptionsProps {
  accounts: IAccountDetails[];
  config: ITrafficManagementConfig;
  onConfigChange: (config: ITrafficManagementConfig) => void;
  selectedAccount: string;
}

export interface IManifestDeploymentOptionsState {
  services: string[];
}

export class ManifestDeploymentOptions extends React.Component<
  IManifestDeploymentOptionsProps,
  IManifestDeploymentOptionsState
> {
  public state: IManifestDeploymentOptionsState = { services: [] };

  private onConfigChange = (key: string, value: any): void => {
    const updatedConfig = cloneDeep(this.props.config);
    set(updatedConfig, key, value);
    this.props.onConfigChange(updatedConfig);
  };

  private fetchServices = (): void => {
    const namespace = this.props.config.options.namespace;
    const account = this.props.selectedAccount;
    if (!namespace || !account) {
      this.setState({
        services: [],
      });
    }
    ManifestKindSearchService.search('service', namespace, account).then(services => {
      this.setState({ services: map(services, 'name') });
    });
  };

  private getNamespaceOptions = (): Array<Option<string>> => {
    const { accounts, selectedAccount } = this.props;
    const selectedAccountDetails = find(accounts, a => a.name === selectedAccount);
    const namespaces = get(selectedAccountDetails, 'namespaces', []);
    return map(namespaces, n => ({ label: n, value: n }));
  };

  private strategyOptionRenderer = (option: IDeploymentStrategy) => {
    return (
      <div className="body-regular">
        <strong>
          <span dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(option.label) }} />
        </strong>
        <div>
          <span dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(option.description) }} />
        </div>
      </div>
    );
  };

  public componentDidMount() {
    this.fetchServices();
  }

  public componentDidUpdate(prevProps: IManifestDeploymentOptionsProps) {
    if (prevProps.selectedAccount !== this.props.selectedAccount) {
      this.onConfigChange('options.namespace', null);
    }

    if (prevProps.config.options.namespace !== this.props.config.options.namespace) {
      this.onConfigChange('options.services', null);
      this.fetchServices();
    }

    if (!this.props.config.options.enableTraffic && !!this.props.config.options.strategy) {
      this.onConfigChange('options.enableTraffic', true);
    }
  }

  public render() {
    const { config } = this.props;
    return (
      <>
        <h4>Rollout Strategy Options</h4>
        <hr />
        <StageConfigField helpKey="kubernetes.manifest.rolloutStrategyOptions" fieldColumns={8} label="Enable">
          <div className="checkbox">
            <label>
              <input
                checked={config.enabled}
                onChange={e => this.onConfigChange('enabled', e.target.checked)}
                type="checkbox"
              />
              Spinnaker manages traffic based on your selected strategy
            </label>
          </div>
        </StageConfigField>
        {config.enabled && (
          <>
            <StageConfigField fieldColumns={8} label="Service(s) Namespace">
              <Select
                clearable={false}
                onChange={(option: Option<string>) => this.onConfigChange('options.namespace', option.value)}
                options={this.getNamespaceOptions()}
                value={config.options.namespace}
              />
            </StageConfigField>
            <StageConfigField fieldColumns={8} label="Service(s)">
              <Select
                clearable={false}
                multi={true}
                onChange={options => this.onConfigChange('options.services', map(options, 'value'))}
                options={map(this.state.services, s => ({ label: split(s, ' ')[1], value: s }))}
                value={config.options.services}
              />
            </StageConfigField>
            <StageConfigField fieldColumns={8} label="Traffic">
              <div className="checkbox">
                <label>
                  <input
                    checked={config.options.enableTraffic}
                    disabled={!!config.options.strategy}
                    onChange={e => this.onConfigChange('options.enableTraffic', e.target.checked)}
                    type="checkbox"
                  />
                  Send client requests to new pods
                </label>
              </div>
            </StageConfigField>
            <StageConfigField fieldColumns={8} helpKey="kubernetes.manifest.rolloutStrategy" label="Strategy">
              <Select
                clearable={false}
                onChange={(option: Option<IDeploymentStrategy>) => this.onConfigChange('options.strategy', option.key)}
                options={rolloutStrategies}
                optionRenderer={this.strategyOptionRenderer}
                placeholder="None"
                value={config.options.strategy}
                valueKey="key"
                valueRenderer={o => <>{o.label}</>}
              />
            </StageConfigField>
          </>
        )}
      </>
    );
  }
}

export const MANIFEST_DEPLOYMENT_OPTIONS = 'spinnaker.kubernetes.v2.pipelines.deployManifest.manifestDeploymentOptions';
module(MANIFEST_DEPLOYMENT_OPTIONS, []).component(
  'manifestDeploymentOptions',
  react2angular(ManifestDeploymentOptions, ['accounts', 'config', 'onConfigChange', 'selectedAccount']),
);
