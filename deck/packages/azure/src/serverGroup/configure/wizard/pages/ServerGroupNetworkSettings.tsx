import React from 'react';

import { AzureWizardPage } from './common';

function getVnetSelectValue(vnet: any): string {
  return `${vnet.name}|${vnet.resourceGroup || ''}`;
}

function getVnetSelectLabel(vnet: any): string {
  return vnet.resourceGroup ? `${vnet.name} (${vnet.resourceGroup})` : vnet.name;
}

export class ServerGroupNetworkSettings extends AzureWizardPage {
  public validate(values: any): { [key: string]: any } {
    const errors: { [key: string]: any } = {};

    if (!values.viewState?.networkSettingsConfigured) {
      return errors;
    }

    if (!values.selectedVnet) {
      errors.selectedVnet = 'Virtual network required.';
    }

    if (!values.subnet) {
      errors.selectedSubnet = 'Subnet required.';
    }

    return errors;
  }

  private selectedVnetChanged = (selected: string) => {
    const vnets = this.props.formik.values.allVnets || this.props.formik.values.backingData?.filtered?.vnets || [];
    const vnet = vnets.find((candidate: any) => getVnetSelectValue(candidate) === selected);
    const selectedVnetSubnets = (vnet?.subnets || [])
      .filter((subnet: any) =>
        (subnet.devices || []).every((device: any) => !device || device.type !== 'applicationGateways'),
      )
      .map((subnet: any) => subnet.name || subnet);
    this.props.formik.values.selectedVnet = vnet || null;
    this.props.formik.values.vnet = vnet?.name || null;
    this.props.formik.values.vnetResourceGroup = vnet?.resourceGroup || null;
    this.props.formik.values.selectedSubnet = null;
    this.props.formik.values.subnet = null;
    this.props.formik.values.selectedVnetSubnets = selectedVnetSubnets;
    this.props.formik.setFieldValue('selectedVnet', vnet || null);
    this.props.formik.setFieldValue('vnet', vnet?.name || null);
    this.props.formik.setFieldValue('vnetResourceGroup', vnet?.resourceGroup || null);
    this.props.formik.setFieldValue('selectedSubnet', null);
    this.props.formik.setFieldValue('subnet', null);
    this.props.formik.setFieldValue('selectedVnetSubnets', selectedVnetSubnets);
  };

  private selectedSubnetChanged = (subnet: string) => {
    this.props.formik.values.selectedSubnet = subnet || null;
    this.props.formik.values.subnet = subnet || null;
    this.props.formik.setFieldValue('selectedSubnet', subnet || null);
    this.props.formik.setFieldValue('subnet', subnet || null);
  };

  public render() {
    const { values } = this.props.formik;
    const vnets = values.allVnets || values.backingData?.filtered?.vnets || [];
    const subnets =
      values.selectedVnetSubnets || (values.selectedVnet?.subnets || []).map((subnet: any) => subnet.name || subnet);
    const showVnetSelect = values.loadBalancerType === 'Azure Load Balancer' || !values.loadBalancerType;

    if (!values.viewState?.networkSettingsConfigured) {
      return <h5 className="text-center">Please select an account, a region and a load balancer first.</h5>;
    }

    return (
      <div className="container-fluid form-horizontal">
        {showVnetSelect && (
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Virtual Network</div>
            <div className="col-md-7">
              <select
                className="form-control input-sm"
                onChange={(event) => this.selectedVnetChanged(event.target.value)}
                value={values.selectedVnet ? getVnetSelectValue(values.selectedVnet) : ''}
              >
                <option value="">Select...</option>
                {vnets.map((vnet: any) => (
                  <option key={getVnetSelectValue(vnet)} value={getVnetSelectValue(vnet)}>
                    {getVnetSelectLabel(vnet)}
                  </option>
                ))}
              </select>
            </div>
          </div>
        )}
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Subnets</div>
          <div className="col-md-7">
            <select
              className="form-control input-sm"
              onChange={(event) => this.selectedSubnetChanged(event.target.value)}
              value={values.subnet || ''}
            >
              <option value="">Select...</option>
              {subnets.map((subnet: string) => (
                <option key={subnet} value={subnet}>
                  {subnet}
                </option>
              ))}
            </select>
          </div>
        </div>
        {!values.viewState?.hideClusterNamePreview && (
          <div className="well-compact text-center">
            <p>Your server group will be using a subnet in virtual network:</p>
            <strong>{values.selectedVnet?.name || 'Virtual network was not selected'}</strong>
          </div>
        )}
      </div>
    );
  }
}
