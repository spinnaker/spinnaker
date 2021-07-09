import React from 'react';
import { Option } from 'react-select';
import VirtualizedSelect from 'react-virtualized-select';

import {
  FirewallLabels,
  HelpField,
  InfrastructureCaches,
  ISecurityGroup,
  ReactInjector,
  timestamp,
} from '@spinnaker/core';

import { IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';

export interface ISecurityGroupSelectorProps {
  command: IAmazonServerGroupCommand;
  availableGroups: ISecurityGroup[];
  hideLabel?: boolean;
  refresh?: () => Promise<void>;
  groupsToEdit: string[];
  helpKey?: string;
  onChange: (securityGroups: string[]) => void;
}

export interface ISecurityGroupSelectorState {
  refreshing: boolean;
  refreshTime: number;
}

export class SecurityGroupSelector extends React.Component<ISecurityGroupSelectorProps, ISecurityGroupSelectorState> {
  constructor(props: ISecurityGroupSelectorProps) {
    super(props);
    this.state = {
      refreshing: false,
      refreshTime: InfrastructureCaches.get('securityGroups').getStats().ageMax,
    };
  }

  private refreshSecurityGroups = () => {
    this.setState({ refreshing: true });
    if (this.props.refresh) {
      this.props.refresh().then(() => this.setState({ refreshing: false }));
    } else {
      (ReactInjector.providerServiceDelegate.getDelegate(
        this.props.command.selectedProvider,
        'serverGroup.configurationService',
      ) as any)
        .refreshSecurityGroups(this.props.command)
        .then(() => {
          this.setState({
            refreshing: false,
            refreshTime: InfrastructureCaches.get('securityGroups').getStats().ageMax,
          });
        });
    }
  };

  private onChange = (options: Option[]) => {
    const securityGroups = options.map((o) => o.value as string);
    this.props.onChange(securityGroups);
  };

  public render() {
    const { availableGroups, groupsToEdit, helpKey, hideLabel } = this.props;
    const { refreshing, refreshTime } = this.state;

    const availableGroupOptions = availableGroups.map((g) => ({ label: `${g.name} (${g.id})`, value: g.id }));

    return (
      <>
        <div className="form-group">
          {!hideLabel && (
            <div className="col-md-3 sm-label-right">
              <b>{FirewallLabels.get('Firewalls')}</b>
              {helpKey && <HelpField key={helpKey} />}
            </div>
          )}
          <div className="col-md-8">
            <VirtualizedSelect
              ignoreAccents={true}
              options={availableGroupOptions}
              onChange={this.onChange}
              value={groupsToEdit}
              multi={true}
            />
          </div>
        </div>

        <div className="form-group small" style={{ marginTop: '20px' }}>
          <div className={`col-md-${hideLabel ? 12 : 9} col-md-offset-${hideLabel ? 0 : 3}`}>
            <p>
              {refreshing && (
                <span>
                  <span className="fa fa-sync-alt fa-spin" />
                </span>
              )}
              {FirewallLabels.get('Firewalls')}
              {!refreshing && <span> last refreshed {timestamp(refreshTime)}</span>}
              {refreshing && <span> refreshing...</span>}
            </p>
            <p>
              If you're not finding a {FirewallLabels.get('firewall')} that was recently added,{' '}
              <a className="clickable" onClick={this.refreshSecurityGroups}>
                click here
              </a>{' '}
              to refresh the list.
            </p>
          </div>
        </div>
      </>
    );
  }
}
