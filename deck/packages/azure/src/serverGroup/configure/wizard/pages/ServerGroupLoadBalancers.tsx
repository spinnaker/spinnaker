import React from 'react';

import { DeckRuntimeContext, InfrastructureCaches, NetworkReader } from '@spinnaker/core';

import { AzureWizardPage } from './common';
import Utility from '../../../../utility';

function normalizeLoadBalancerType(loadBalancerType: string): string | null {
  const normalized = String(loadBalancerType || '')
    .toLowerCase()
    .split('_')
    .join(' ');
  if (normalized === 'application gateway') {
    return 'Azure Application Gateway';
  }
  if (normalized === 'load balancer') {
    return 'Azure Load Balancer';
  }
  return Utility.getLoadBalancerType(loadBalancerType)?.type || loadBalancerType || null;
}

function selectableSubnetNames(vnet: any): string[] {
  return (vnet?.subnets || [])
    .filter((subnet: any) =>
      (subnet.devices || []).every((device: any) => !device || device.type !== 'applicationGateways'),
    )
    .map((subnet: any) => subnet.name || subnet);
}

function matchesSelectedVnet(candidate: any, selectedVnet: any): boolean {
  return (
    candidate.name === selectedVnet.name &&
    (!selectedVnet.resourceGroup || candidate.resourceGroup === selectedVnet.resourceGroup)
  );
}

export class ServerGroupLoadBalancers extends AzureWizardPage {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  private loadVnetSubnetsRequestId = 0;

  private getCommandLoadBalancer(loadBalancerName: string | null): any {
    const { values } = this.props.formik;
    const loadBalancers = values.backingData?.loadBalancers || [];
    return (
      loadBalancers.find(
        (candidate: any) =>
          candidate.name === loadBalancerName &&
          candidate.account === values.credentials &&
          candidate.region === values.region,
      ) || loadBalancers.find((candidate: any) => candidate.name === loadBalancerName)
    );
  }

  public componentDidMount(): void {
    const { values } = this.props.formik;
    if (values.credentials && values.region) {
      values.viewState.networkSettingsConfigured = true;
      values.selectedVnetSubnets = values.selectedVnetSubnets || [];
      if (values.loadBalancerName) {
        void this.loadBalancerChanged(values.loadBalancerName);
      } else {
        void this.loadVnetSubnets(null, values.loadBalancerType || null, ++this.loadVnetSubnetsRequestId);
      }
    }
  }

  public componentWillUnmount(): void {
    this.loadVnetSubnetsRequestId += 1;
  }

  public loadBalancerChanged = async (loadBalancerName: string | null, clearNetworkFields = false): Promise<void> => {
    const { values } = this.props.formik;
    const requestId = ++this.loadVnetSubnetsRequestId;
    const loadBalancer = this.getCommandLoadBalancer(loadBalancerName);
    const loadBalancerType = normalizeLoadBalancerType(loadBalancer?.loadBalancerType);
    values.loadBalancerName = loadBalancerName || null;
    values.loadBalancerType = loadBalancerType;
    values.selectedVnetSubnets = [];
    if (clearNetworkFields) {
      values.selectedVnet = null;
      values.vnet = null;
      values.vnetResourceGroup = null;
      values.selectedSubnet = null;
      values.subnet = null;
    }
    values.viewState = values.viewState || {};
    values.viewState.networkSettingsConfigured = true;
    this.props.formik.setFieldValue('loadBalancerName', values.loadBalancerName);
    this.props.formik.setFieldValue('loadBalancerType', values.loadBalancerType);
    this.props.formik.setFieldValue('selectedVnetSubnets', []);
    if (clearNetworkFields) {
      this.props.formik.setFieldValue('selectedVnet', null);
      this.props.formik.setFieldValue('vnet', null);
      this.props.formik.setFieldValue('vnetResourceGroup', null);
      this.props.formik.setFieldValue('selectedSubnet', null);
      this.props.formik.setFieldValue('subnet', null);
    }
    InfrastructureCaches.clearCache('networks');
    await this.loadVnetSubnets(values.loadBalancerName, loadBalancerType, requestId);
  };

  private loadVnetSubnets = async (
    loadBalancerName: string | null,
    loadBalancerType: string | null,
    requestId: number,
  ): Promise<void> => {
    const { values } = this.props.formik;
    const credentials = values.credentials;
    const region = values.region;
    if (!credentials || !region) {
      return;
    }

    let loadBalancerDetails: any[];
    let networks: any;
    try {
      [loadBalancerDetails, networks] = await Promise.all([
        loadBalancerName
          ? this.context.services.loadBalancerReader.getLoadBalancerDetails(
              'azure',
              credentials,
              region,
              loadBalancerName,
            )
          : Promise.resolve([]),
        NetworkReader.listNetworks(),
      ]);
    } catch (_error) {
      return;
    }
    if (
      requestId !== this.loadVnetSubnetsRequestId ||
      values.loadBalancerName !== loadBalancerName ||
      values.credentials !== credentials ||
      values.region !== region
    ) {
      return;
    }
    const azureNetworks = Array.isArray(networks) ? networks : (networks as any).azure || [];
    const allVnets = azureNetworks.filter((vnet: any) => vnet.account === credentials && vnet.region === region);
    const attachedVnet = values.selectedVnet;
    const selectedLoadBalancer = loadBalancerDetails?.length === 1 ? loadBalancerDetails[0] : null;
    let selectedVnet =
      !selectedLoadBalancer && attachedVnet
        ? allVnets.find((vnet: any) => matchesSelectedVnet(vnet, attachedVnet)) || null
        : null;

    if (selectedLoadBalancer) {
      selectedVnet = allVnets.find((vnet: any) => {
        if (loadBalancerType === 'Azure Application Gateway') {
          return vnet.name === selectedLoadBalancer.vnet;
        }
        if (loadBalancerType === 'Azure Load Balancer' && attachedVnet) {
          return matchesSelectedVnet(vnet, attachedVnet);
        }
        return false;
      });
    }

    const selectedVnetSubnets = selectedVnet
      ? selectableSubnetNames(selectedVnet)
      : allVnets.reduce((subnets: string[], vnet: any) => subnets.concat(selectableSubnetNames(vnet)), []);

    values.allVnets = allVnets;
    values.selectedVnet = selectedVnet || null;
    values.vnet = selectedVnet?.name || null;
    values.vnetResourceGroup = selectedVnet?.resourceGroup || null;
    values.selectedVnetSubnets = selectedVnetSubnets;
    values.selectedSubnet = selectedVnet && selectedVnetSubnets.includes(values.subnet) ? values.subnet : null;
    values.subnet = values.selectedSubnet;

    this.props.formik.setFieldValue('allVnets', allVnets);
    this.props.formik.setFieldValue('selectedVnet', values.selectedVnet);
    this.props.formik.setFieldValue('vnet', values.vnet);
    this.props.formik.setFieldValue('vnetResourceGroup', values.vnetResourceGroup);
    this.props.formik.setFieldValue('selectedVnetSubnets', selectedVnetSubnets);
    this.props.formik.setFieldValue('selectedSubnet', values.selectedSubnet);
    this.props.formik.setFieldValue('subnet', values.subnet);
  };

  public render() {
    const loadBalancers =
      this.props.formik.values.loadBalancers || this.props.formik.values.backingData?.filtered?.loadBalancers || [];
    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Load Balancer</div>
          <div className="col-md-7">
            <select
              className="form-control input-sm"
              onChange={(event) => this.loadBalancerChanged(event.target.value, true)}
              value={this.props.formik.values.loadBalancerName || ''}
            >
              <option value="">None</option>
              {loadBalancers.map((loadBalancer: string) => (
                <option key={loadBalancer} value={loadBalancer}>
                  {loadBalancer}
                </option>
              ))}
            </select>
          </div>
        </div>
        {this.props.formik.values.loadBalancerName && (
          <div className="well-compact text-center">
            The load balancer {this.props.formik.values.loadBalancerName} is an{' '}
            {this.props.formik.values.loadBalancerType}
          </div>
        )}
      </div>
    );
  }
}
