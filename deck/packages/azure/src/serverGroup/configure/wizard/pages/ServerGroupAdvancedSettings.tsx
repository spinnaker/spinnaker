import React from 'react';

import { AzureWizardPage } from './common';

export class ServerGroupAdvancedSettings extends AzureWizardPage {
  public addDataDisk = () => {
    const dataDisks = [
      ...(this.props.formik.values.dataDisks || []),
      {
        lun: 0,
        managedDisk: {
          storageAccountType: 'Standard_LRS',
        },
        diskSizeGB: 1,
        caching: 'None',
        createOption: 'Empty',
      },
    ];
    this.props.formik.values.dataDisks = dataDisks;
    this.props.formik.setFieldValue('dataDisks', dataDisks);
  };

  private removeDataDisk = (index: number) => {
    const dataDisks = [...(this.props.formik.values.dataDisks || [])];
    dataDisks.splice(index, 1);
    this.props.formik.values.dataDisks = dataDisks;
    this.props.formik.setFieldValue('dataDisks', dataDisks);
  };

  private updateDataDisk = (index: number, field: string, value: any) => {
    const dataDisks = [...(this.props.formik.values.dataDisks || [])];
    const disk = { ...(dataDisks[index] || {}) };
    if (field === 'storageAccountType') {
      disk.managedDisk = { ...(disk.managedDisk || {}), storageAccountType: value };
    } else {
      disk[field] = value;
    }
    dataDisks[index] = disk;
    this.props.formik.values.dataDisks = dataDisks;
    this.props.formik.setFieldValue('dataDisks', dataDisks);
  };

  public render() {
    const { values } = this.props.formik;
    values.osConfig = values.osConfig || {};
    values.customScriptsSettings = values.customScriptsSettings || {};
    values.dataDisks = values.dataDisks || [];
    const dataDiskTypes = values.backingData?.dataDiskTypes || ['Standard_LRS', 'StandardSSD_LRS', 'Premium_LRS'];
    const dataDiskCachingTypes = values.backingData?.dataDiskCachingTypes || ['None', 'ReadOnly', 'ReadWrite'];

    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <div className="col-md-4 sm-label-right">Number of Instances</div>
          <div className="col-md-2">
            <input
              className="form-control input-sm"
              max="100"
              min="0"
              onChange={(event) => this.setField('sku.capacity', Number(event.target.value))}
              type="number"
              value={values.sku?.capacity ?? 0}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-4 sm-label-right">Custom Data</div>
          <div className="col-md-7">
            <input
              className="form-control input-sm"
              onChange={(event) => this.setField('osConfig.customData', event.target.value)}
              value={values.osConfig.customData || ''}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-4 sm-label-right">Custom Script</div>
          <div className="col-md-7">
            <input
              className="form-control input-sm"
              onChange={(event) => this.setField('customScriptsSettings.fileUris', event.target.value)}
              value={values.customScriptsSettings.fileUris || ''}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-4 sm-label-right">Command To Execute</div>
          <div className="col-md-7">
            <input
              className="form-control input-sm"
              onChange={(event) => this.setField('customScriptsSettings.commandToExecute', event.target.value)}
              value={values.customScriptsSettings.commandToExecute || ''}
            />
          </div>
        </div>
        {values.loadBalancerType === 'Azure Application Gateway' && (
          <div className="form-group">
            <div className="col-md-4 sm-label-right">
              <input
                checked={!!values.enableInboundNAT}
                disabled={!!values.zonesEnabled}
                onChange={(event) => this.setField('enableInboundNAT', event.target.checked)}
                type="checkbox"
              />
            </div>
            <div className="col-md-7">
              <b>Enable inbound NAT port-forwarding rules to connect to VM instances</b>
            </div>
          </div>
        )}
        <div className="form-group">
          <div className="col-md-4 sm-label-right">User-Assigned Identities</div>
          <div className="col-md-7">
            <input
              className="form-control input-sm"
              onChange={(event) => this.setField('userAssignedIdentities', event.target.value)}
              value={values.userAssignedIdentities || ''}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-4 sm-label-left">Data Disks</div>
          <div className="col-md-11">
            <table className="table table-condensed packed tags">
              <thead>
                <tr>
                  <th>LUN</th>
                  <th>Size (GB)</th>
                  <th>Type</th>
                  <th>Caching</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {values.dataDisks.map((disk: any, index: number) => (
                  <tr key={index}>
                    <td>
                      <input
                        className="form-control input-sm"
                        min="0"
                        onChange={(event) => this.updateDataDisk(index, 'lun', Number(event.target.value))}
                        type="number"
                        value={disk.lun ?? 0}
                      />
                    </td>
                    <td>
                      <input
                        className="form-control input-sm"
                        min="1"
                        onChange={(event) => this.updateDataDisk(index, 'diskSizeGB', Number(event.target.value))}
                        type="number"
                        value={disk.diskSizeGB ?? 1}
                      />
                    </td>
                    <td>
                      <select
                        className="form-control input-sm"
                        onChange={(event) => this.updateDataDisk(index, 'storageAccountType', event.target.value)}
                        value={disk.managedDisk?.storageAccountType || ''}
                      >
                        {dataDiskTypes.map((type: string) => (
                          <option key={type} value={type}>
                            {type}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <select
                        className="form-control input-sm"
                        onChange={(event) => this.updateDataDisk(index, 'caching', event.target.value)}
                        value={disk.caching || ''}
                      >
                        {dataDiskCachingTypes.map((type: string) => (
                          <option key={type} value={type}>
                            {type}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <button
                        className="btn btn-link sm-label"
                        onClick={() => this.removeDataDisk(index)}
                        type="button"
                      >
                        <span className="glyphicon glyphicon-trash" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={5}>
                    <button className="btn btn-block btn-sm add-new" onClick={this.addDataDisk} type="button">
                      <span className="glyphicon glyphicon-plus-sign" /> Add New Data Disk
                    </button>
                  </td>
                </tr>
              </tfoot>
            </table>
          </div>
        </div>
      </div>
    );
  }
}
