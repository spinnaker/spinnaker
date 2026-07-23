import { cloneDeep, get, has, set, uniq } from 'lodash';
import React from 'react';

import type { IApplicationProviderField } from '../../cloudProvider';
import { CloudProviderRegistry } from '../../cloudProvider';
import { SETTINGS } from '../../config/settings';
import { HelpField } from '../../help/HelpField';
import type { IApplicationAttributes } from '../service/ApplicationWriter';

export interface IApplicationProviderFieldsProps {
  application: IApplicationAttributes;
  availableProviders: string[];
  selectedProviders: string[];
  onChange: (application: IApplicationAttributes) => void;
}

interface IProviderFields {
  fields: IApplicationProviderField[];
  provider: string;
}

export class ApplicationProviderFields extends React.Component<IApplicationProviderFieldsProps> {
  public componentDidMount(): void {
    this.initializeDefaults();
  }

  public componentDidUpdate(previousProps: IApplicationProviderFieldsProps): void {
    if (
      previousProps.application !== this.props.application ||
      previousProps.availableProviders !== this.props.availableProviders ||
      previousProps.selectedProviders !== this.props.selectedProviders
    ) {
      this.initializeDefaults();
    }
  }

  private getProviderFields(): IProviderFields[] {
    const selectedProviders = Array.isArray(this.props.selectedProviders) ? this.props.selectedProviders : [];
    const providers = uniq(selectedProviders.length ? selectedProviders : this.props.availableProviders || []);

    return providers
      .map((provider) => ({
        fields: CloudProviderRegistry.getValue(provider, 'applicationProviderFields') || [],
        provider,
      }))
      .filter(({ fields }) => fields.length > 0);
  }

  private initializeDefaults(): void {
    let changed = false;
    const application = cloneDeep(this.props.application);

    this.getProviderFields().forEach(({ fields, provider }) => {
      fields.forEach(({ field }) => {
        const applicationPath = `providerSettings.${provider}.${field}`;
        const settingsPath = `${provider}.${field}`;
        if (!has(application, applicationPath) && has(SETTINGS.providers, settingsPath)) {
          set(application, applicationPath, cloneDeep(get(SETTINGS.providers, settingsPath)));
          changed = true;
        }
      });
    });

    if (changed) {
      this.props.onChange(application);
    }
  }

  private updateField = (provider: string, field: string, value: boolean): void => {
    const application = cloneDeep(this.props.application);
    set(application, `providerSettings.${provider}.${field}`, value);
    this.props.onChange(application);
  };

  public render(): React.ReactNode {
    return this.getProviderFields().map(({ fields, provider }) => (
      <div className="form-group row" data-provider-fields={provider} key={provider}>
        <div className="col-sm-3 sm-label-right">{provider.toUpperCase()} Settings</div>
        <div className="col-sm-9 checkbox" style={{ marginBottom: 0, marginTop: '5px' }}>
          {fields.map(({ field, helpKey, label, type }) =>
            type === 'boolean' ? (
              <label key={field} style={{ display: 'block' }}>
                <input
                  checked={Boolean(get(this.props.application, `providerSettings.${provider}.${field}`, false))}
                  data-field={field}
                  data-provider={provider}
                  type="checkbox"
                  onChange={(event) => this.updateField(provider, field, event.target.checked)}
                />{' '}
                {label} {helpKey && <HelpField id={helpKey} />}
              </label>
            ) : null,
          )}
        </div>
      </div>
    ));
  }
}
