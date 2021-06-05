import { module } from 'angular';
import { isEqual, uniqWith } from 'lodash';
import * as React from 'react';
import { Alert } from 'react-bootstrap';
import { Option } from 'react-select';
import { react2angular } from 'react2angular';

import { HelpField, ISubnet, TetheredSelect, withErrorBoundary } from '@spinnaker/core';

import { IEcsServerGroupCommand } from '../../serverGroupConfiguration.service';

export interface IEcsNetworkingProps {
  command: IEcsServerGroupCommand;
  notifyAngular: (key: string, value: any) => void;
  configureCommand: (query: string) => PromiseLike<void>;
}

interface IEcsNetworkingState {
  associatePublicIpAddress: boolean;
  networkMode: string;
  networkModesAvailable: string[];
  securityGroupNames: string[];
  securityGroupsAvailable: string[];
  subnetTypes: string[];
  subnetTypesAvailable: ISubnet[];
}

export class EcsNetworking extends React.Component<IEcsNetworkingProps, IEcsNetworkingState> {
  constructor(props: IEcsNetworkingProps) {
    super(props);
    const cmd = this.props.command;

    let defaultSubnetTypes: string[] = [];
    if (cmd.subnetTypes && cmd.subnetTypes.length > 0) {
      defaultSubnetTypes = cmd.subnetTypes;
    }

    if (cmd.subnetType && cmd.subnetType.length > 0) {
      defaultSubnetTypes.push(cmd.subnetType);
      cmd.subnetType = '';
    }

    cmd.subnetTypes = uniqWith(defaultSubnetTypes, isEqual);

    this.state = {
      associatePublicIpAddress: cmd.associatePublicIpAddress,
      networkMode: cmd.networkMode,
      networkModesAvailable: cmd.backingData && cmd.backingData.networkModes ? cmd.backingData.networkModes : [],
      securityGroupNames: cmd.securityGroupNames,
      securityGroupsAvailable:
        cmd.backingData && cmd.backingData.filtered && cmd.backingData.filtered.securityGroupNames
          ? cmd.backingData.filtered.securityGroupNames
          : [],
      subnetTypes: cmd.subnetTypes,
      subnetTypesAvailable:
        cmd.backingData && cmd.backingData.filtered && cmd.backingData.filtered.subnetTypes
          ? cmd.backingData.filtered.subnetTypes
          : [],
    };
  }

  public componentDidMount() {
    const cmd = this.props.command;

    this.props.configureCommand('1').then(() => {
      this.setState({
        networkModesAvailable: cmd.backingData && cmd.backingData.networkModes ? cmd.backingData.networkModes : [],
        securityGroupsAvailable:
          cmd.backingData && cmd.backingData.filtered && cmd.backingData.filtered.securityGroupNames
            ? cmd.backingData.filtered.securityGroupNames
            : [],
        subnetTypesAvailable:
          cmd.backingData && cmd.backingData.filtered && cmd.backingData.filtered.subnetTypes
            ? cmd.backingData.filtered.subnetTypes
            : [],
      });
    });
  }

  private updateNetworkMode = (newNetworkMode: Option<string>) => {
    const updatedNetworkMode = newNetworkMode.value;
    this.props.notifyAngular('networkMode', updatedNetworkMode);
    this.setState({ networkMode: updatedNetworkMode });
  };

  private updateSecurityGroups = (newSecurityGroups: Option<string>) => {
    const updatedSecurityGroups = Array.isArray(newSecurityGroups)
      ? newSecurityGroups.map((securityGroups) => securityGroups.value)
      : [];
    this.props.notifyAngular('securityGroupNames', updatedSecurityGroups);
    this.setState({ securityGroupNames: updatedSecurityGroups });
  };

  private updateSubnetTypes = (newSubnetTypes: Option<string>) => {
    const cmd = this.props.command;
    const updatedSubnetTypes = Array.isArray(newSubnetTypes)
      ? newSubnetTypes.map((subnetType) => subnetType.value)
      : [];
    this.props.notifyAngular('subnetTypes', updatedSubnetTypes);
    cmd.subnetTypeChanged(cmd);
    this.setState({
      subnetTypes: updatedSubnetTypes,
      securityGroupsAvailable:
        cmd.backingData && cmd.backingData.filtered && cmd.backingData.filtered.securityGroupNames
          ? cmd.backingData.filtered.securityGroupNames
          : [],
    });
  };

  private updateAssociatePublicIpAddress = (usePublicIp: boolean) => {
    this.props.notifyAngular('associatePublicIpAddress', usePublicIp);
    this.setState({ associatePublicIpAddress: usePublicIp });
  };

  public render(): React.ReactElement<EcsNetworking> {
    const updateAssociatePublicIpAddress = this.updateAssociatePublicIpAddress;
    const updateNetworkMode = this.updateNetworkMode;
    const updateSecurityGroups = this.updateSecurityGroups;
    const updateSubnetTypes = this.updateSubnetTypes;

    const networkModesAvailable = this.state.networkModesAvailable.map(function (networkMode) {
      return { label: `${networkMode}`, value: networkMode };
    });

    const securityGroupsAvailable = this.state.securityGroupsAvailable.map(function (securityGroup) {
      return { label: `${securityGroup}`, value: securityGroup };
    });

    const subnetTypesAvailable = this.state.subnetTypesAvailable.map(function (subnetType) {
      return { label: `${subnetType.purpose} (${subnetType.vpcId})`, value: subnetType.purpose };
    });

    const subnetTypeOptions = this.state.subnetTypesAvailable.length ? (
      <TetheredSelect
        multi={true}
        options={subnetTypesAvailable}
        value={this.state.subnetTypes}
        onChange={(e: Option) => {
          updateSubnetTypes(e as Option<string>);
        }}
      />
    ) : (
      <Alert color="warning">No account was selected, or no subnet types are available for this account</Alert>
    );

    const securityGroupsOptions = this.state.securityGroupsAvailable.length ? (
      <TetheredSelect
        multi={true}
        options={securityGroupsAvailable}
        value={this.state.securityGroupNames}
        onChange={(e: Option) => {
          updateSecurityGroups(e as Option<string>);
        }}
      />
    ) : (
      <Alert color="warning">No security groups found in the selected account/region</Alert>
    );

    const awsVpcOptions =
      this.state.networkMode === 'awsvpc' ? (
        <div className="form-group">
          <div className="form-group">
            <div className="col-md-3 sm-label-right">
              <b>VPC Subnet</b>
              <HelpField key="ecs.subnet" />
            </div>
            <div className="col-md-9" data-test-id="Networking.subnetType">
              {subnetTypeOptions}
            </div>
          </div>

          <div className="form-group">
            <div className="col-md-3 sm-label-right">
              <b>Security Groups</b>
              <HelpField key="ecs.securityGroups" />
            </div>
            <div className="col-md-9" data-test-id="Networking.securityGroups">
              {securityGroupsOptions}
            </div>
          </div>

          <div className="form-group">
            <div className="col-md-3 sm-label-right">
              <b>Associate Public IP Address</b>
              <HelpField key="ecs.publicip" />
            </div>
            <div className="col-md-1 radio">
              <label>
                <input
                  data-test-id="Networking.associatePublicIpAddressTrue"
                  type="radio"
                  value="true"
                  id="associatePublicIpAddressTrue"
                  checked={this.state.associatePublicIpAddress === true}
                  onChange={() => updateAssociatePublicIpAddress(true)}
                />
                Yes
              </label>
            </div>
            <div className="col-md-1 radio">
              <label>
                <input
                  data-test-id="Networking.associatePublicIpAddressFalse"
                  type="radio"
                  value="false"
                  id="associatePublicIpAddressFalse"
                  checked={this.state.associatePublicIpAddress === false}
                  onChange={() => updateAssociatePublicIpAddress(false)}
                />
                No
              </label>
            </div>
          </div>
        </div>
      ) : (
        <div className="col-md-3 sm-label-right"></div>
      );

    return (
      <div className="networking-fluid form-horizontal">
        <div className="form-group">
          <div className="form-group">
            <div className="col-md-3 sm-label-right">
              <b>Network Mode</b>
              <HelpField id="ecs.networkMode" />
            </div>
            <div className="col-md-9" data-test-id="Networking.networkMode">
              <TetheredSelect
                placeholder="Select a network mode to use ..."
                options={networkModesAvailable}
                value={this.state.networkMode}
                onChange={(e: Option) => {
                  updateNetworkMode(e as Option<string>);
                }}
              />
            </div>
          </div>
        </div>
        {awsVpcOptions}
      </div>
    );
  }
}

export const ECS_NETWORKING_REACT = 'spinnaker.ecs.serverGroup.configure.wizard.networking.react';
module(ECS_NETWORKING_REACT, []).component(
  'ecsNetworkingReact',
  react2angular(withErrorBoundary(EcsNetworking, 'ecsNetworkingReact'), [
    'command',
    'notifyAngular',
    'configureCommand',
  ]),
);
