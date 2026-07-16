import React from 'react';

import { AzureWizardPage } from './common';

const healthCheckProtocols = [
  { displayName: 'N/A', name: '' },
  { displayName: 'HTTP', name: 'http' },
  { displayName: 'TCP', name: 'tcp' },
];

export class ServerGroupHealthSettings extends AzureWizardPage {
  public validate(values: any): { [key: string]: any } {
    if (!values.healthSettings?.protocol) {
      return {};
    }
    const errors: { [key: string]: any } = {};
    if (!values.healthSettings.port) {
      errors.port = 'Port required.';
    }
    if (values.healthSettings.protocol === 'http' && !values.healthSettings.requestPath) {
      errors.requestPath = 'Path required.';
    }
    return errors;
  }

  private updateHealthSettings = (updates: any) => {
    const healthSettings = { ...(this.props.formik.values.healthSettings || {}), ...updates };
    this.props.formik.values.healthSettings = healthSettings;
    this.props.formik.setFieldValue('healthSettings', healthSettings);
  };

  private protocolChanged = (protocol: string) => {
    if (!protocol) {
      this.updateHealthSettings({ protocol: null, port: null, requestPath: null });
      return;
    }
    this.updateHealthSettings({
      protocol,
      requestPath: protocol === 'http' ? this.props.formik.values.healthSettings?.requestPath : null,
    });
  };

  public render() {
    const healthSettings = this.props.formik.values.healthSettings || {};
    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Protocol</div>
          <div className="col-md-7">
            <select
              className="form-control input-sm"
              onChange={(event) => this.protocolChanged(event.target.value)}
              value={healthSettings.protocol || ''}
            >
              {healthCheckProtocols.map((protocol) => (
                <option key={protocol.displayName} value={protocol.name}>
                  {protocol.displayName}
                </option>
              ))}
            </select>
          </div>
        </div>
        {healthSettings.protocol && (
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Port</div>
            <div className="col-md-7">
              <input
                className="form-control input-sm"
                onChange={(event) => this.updateHealthSettings({ port: event.target.value })}
                type="text"
                value={healthSettings.port || ''}
              />
            </div>
          </div>
        )}
        {healthSettings.protocol === 'http' && (
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Path</div>
            <div className="col-md-7">
              <input
                className="form-control input-sm"
                onChange={(event) => this.updateHealthSettings({ requestPath: event.target.value })}
                type="text"
                value={healthSettings.requestPath || ''}
              />
            </div>
          </div>
        )}
        <div className="form-group small" style={{ marginTop: '20px' }}>
          <div className="col-md-9 col-md-offset-3">
            <p>Health settings here will be applied directly to the VM Scale Set.</p>
          </div>
        </div>
      </div>
    );
  }
}
