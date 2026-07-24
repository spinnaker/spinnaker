import type { FormikProps } from 'formik';
import React from 'react';

import { HelpField } from '@spinnaker/core';

import type { IProxmoxServerGroupCommand } from '../proxmoxServerGroupCommandBuilder';

export interface IProxmoxServerGroupVMSettingsProps {
  formik: FormikProps<IProxmoxServerGroupCommand>;
}

export class ProxmoxServerGroupVMSettings extends React.Component<IProxmoxServerGroupVMSettingsProps> {
  private setValue = (field: keyof IProxmoxServerGroupCommand, value: any): void => {
    this.props.formik.setFieldValue(field, value);
  };

  private numberInput(
    label: string,
    field: keyof IProxmoxServerGroupCommand,
    help?: string,
    qemuOnly = false,
  ): JSX.Element {
    const { values } = this.props.formik;
    if (qemuOnly && values.vmType === 'lxc') {
      return null;
    }
    return (
      <div className="form-group">
        <div className="col-md-3 sm-label-right">
          {label} {help && <HelpField content={help} />}
        </div>
        <div className="col-md-7">
          <input
            type="number"
            className="form-control input-sm no-spel"
            value={(values[field] as number) ?? ''}
            onChange={(e) => this.setValue(field, e.target.value ? Number(e.target.value) : undefined)}
          />
        </div>
      </div>
    );
  }

  private textInput(label: string, field: keyof IProxmoxServerGroupCommand, help?: string): JSX.Element {
    const { values } = this.props.formik;
    return (
      <div className="form-group">
        <div className="col-md-3 sm-label-right">
          {label} {help && <HelpField content={help} />}
        </div>
        <div className="col-md-7">
          <input
            type="text"
            className="form-control input-sm no-spel"
            value={(values[field] as string) ?? ''}
            onChange={(e) => this.setValue(field, e.target.value)}
          />
        </div>
      </div>
    );
  }

  public render() {
    const { values } = this.props.formik;
    const isQemu = values.vmType !== 'lxc';

    return (
      <div className="form-horizontal">
        {this.numberInput('Cores', 'cores', 'CPU core count applied after clone.')}
        {this.numberInput('Sockets', 'sockets', 'CPU socket count applied after clone.', true)}
        {this.numberInput('Memory (MB)', 'memory', 'Memory in MB applied after clone.')}
        {this.textInput(
          'Disk Size',
          'diskSize',
          'Target disk size applied after clone, e.g. "20G" absolute or "+10G" relative. Leave blank to keep the template disk size.',
        )}
        {isQemu && this.textInput('Disk Device', 'diskDevice', 'Disk device identifier to resize (default scsi0).')}
        {this.textInput('Network (net0)', 'net0', 'Network config override, e.g. "virtio,bridge=vmbr0".')}
        {isQemu &&
          this.textInput(
            'Cloud-Init IP (ipconfig0)',
            'ipconfig0',
            'Cloud-init IP config for the first interface, e.g. "ip=dhcp" or "ip=192.168.1.100/24,gw=192.168.1.1".',
          )}
        {isQemu && (
          <div className="form-group">
            <div className="col-md-3 sm-label-right">
              BIOS{' '}
              <HelpField content="Leave as inherit to keep the template BIOS. Selecting OVMF provisions an EFI disk." />
            </div>
            <div className="col-md-7">
              <select
                className="form-control input-sm"
                value={values.bios ?? ''}
                onChange={(e) => this.setValue('bios', e.target.value)}
              >
                <option value="">(inherit from template)</option>
                <option value="seabios">SeaBIOS</option>
                <option value="ovmf">OVMF (UEFI)</option>
              </select>
            </div>
          </div>
        )}
      </div>
    );
  }
}
