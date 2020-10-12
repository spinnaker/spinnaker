import React from 'react';

import { FirewallLabels, noop } from '@spinnaker/core';

import { IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';

export interface IServerGroupSecurityGroupsRemovedProps {
  command?: IAmazonServerGroupCommand;
  removed?: string[];
  onClear?: () => void;
}

export class ServerGroupSecurityGroupsRemoved extends React.Component<IServerGroupSecurityGroupsRemovedProps> {
  public static defaultProps: Partial<IServerGroupSecurityGroupsRemovedProps> = {
    onClear: noop,
  };

  public render() {
    const { command, onClear, removed } = this.props;

    const dirtySecurityGroups = ((command && command.viewState.dirty.securityGroups) || []).concat(removed || []);

    if (dirtySecurityGroups.length === 0) {
      return null;
    }

    return (
      <div className="col-md-12">
        <div className="alert alert-warning">
          <p>
            <i className="fa fa-exclamation-triangle" />
            The following {FirewallLabels.get('firewalls')} could not be found in the selected account/region/VPC and
            were removed:
          </p>
          <ul>
            {dirtySecurityGroups.map((s) => (
              <li key={s}>{s}</li>
            ))}
          </ul>
          <p className="text-right">
            <a className="btn btn-sm btn-default dirty-flag-dismiss clickable" onClick={onClear}>
              Okay
            </a>
          </p>
        </div>
      </div>
    );
  }
}
