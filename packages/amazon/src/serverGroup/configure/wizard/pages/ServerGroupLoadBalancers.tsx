import { FormikProps } from 'formik';
import React from 'react';
import { Option } from 'react-select';

import { HelpField, IWizardPageComponent, ReactInjector, TetheredSelect } from '@spinnaker/core';

import { IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';

export interface IServerGroupLoadBalancersProps {
  hideLoadBalancers?: boolean;
  hideTargetGroups?: boolean;
  formik: FormikProps<IAmazonServerGroupCommand>;
}

export interface IServerGroupLoadBalancersState {
  refreshed: boolean;
  refreshing: boolean;
  showVpcLoadBalancers: boolean;
}

const stringToOption = (value: string): Option<string> => {
  return { value, label: value };
};

export class ServerGroupLoadBalancers
  extends React.Component<IServerGroupLoadBalancersProps, IServerGroupLoadBalancersState>
  implements IWizardPageComponent<IAmazonServerGroupCommand> {
  public state = {
    refreshing: false,
    refreshed: false,
    showVpcLoadBalancers: false,
  };

  public validate(values: IAmazonServerGroupCommand) {
    const errors = {} as any;

    if (values.viewState.dirty.targetGroups) {
      errors.targetGroups = 'You must confirm the removed target groups.';
    }
    if (values.viewState.dirty.loadBalancers) {
      errors.loadBalancers = 'You must confirm the removed load balancers.';
    }

    return errors;
  }

  public refreshLoadBalancers = () => {
    const { values } = this.props.formik;
    this.setState({ refreshing: true });
    const configurationService: any = ReactInjector.providerServiceDelegate.getDelegate(
      values.cloudProvider || values.selectedProvider,
      'serverGroup.configurationService',
    );
    configurationService.refreshLoadBalancers(values).then(() => {
      this.setState({
        refreshing: false,
        refreshed: true,
      });
    });
  };

  public clearWarnings(key: 'loadBalancers' | 'targetGroups'): void {
    this.props.formik.values.viewState.dirty[key] = null;
    this.props.formik.validateForm();
  }

  private targetGroupsChanged = (options: Array<Option<string>>) => {
    const targetGroups = options.map((o) => o.value);
    this.props.formik.setFieldValue('targetGroups', targetGroups);
  };

  private loadBalancersChanged = (options: Array<Option<string>>) => {
    const loadBalancers = options.map((o) => o.value);
    this.props.formik.setFieldValue('loadBalancers', loadBalancers);
  };

  private vpcLoadBalancersChanged = (options: Array<Option<string>>) => {
    const vpcLoadBalancers = options.map((o) => o.value);
    this.props.formik.setFieldValue('vpcLoadBalancers', vpcLoadBalancers);
  };

  public render() {
    const { hideLoadBalancers, hideTargetGroups } = this.props;
    const { values } = this.props.formik;
    const { dirty } = values.viewState;
    const { refreshed, refreshing, showVpcLoadBalancers } = this.state;

    let targetGroupSection = null;
    if (!hideTargetGroups) {
      const targetGroupOptions = (values.backingData.filtered.targetGroups || [])
        .concat(values.viewState.spelTargetGroups || [])
        .map(stringToOption);

      targetGroupSection = (
        <>
          {dirty.targetGroups && (
            <div className="col-md-12">
              <div className="alert alert-warning">
                <p>
                  <i className="fa fa-exclamation-triangle" />
                  The following target groups could not be found in the selected account/region/VPC and were removed:
                </p>
                <ul>
                  {dirty.targetGroups.map((tg) => (
                    <li key={tg}>{tg}</li>
                  ))}
                </ul>
                <p className="text-right">
                  <a
                    className="btn btn-sm btn-default dirty-flag-dismiss clickable"
                    onClick={() => this.clearWarnings('targetGroups')}
                  >
                    Okay
                  </a>
                </p>
              </div>
            </div>
          )}
          <div className="form-group">
            <div className="col-md-4 sm-label-right">
              <b>Target Groups </b>
              <HelpField id="aws.loadBalancer.targetGroups" />
            </div>
            <div className="col-md-7">
              {targetGroupOptions.length === 0 && (
                <div className="form-control-static">No target groups found in the selected account/region/VPC</div>
              )}
              {targetGroupOptions.length > 0 && (
                <TetheredSelect
                  multi={true}
                  options={targetGroupOptions}
                  value={values.targetGroups}
                  onChange={this.targetGroupsChanged}
                />
              )}
            </div>
          </div>
        </>
      );
    }

    let loadBalancersSection = null;
    if (!hideLoadBalancers) {
      const loadBalancerOptions = (values.backingData.filtered.loadBalancers || [])
        .concat(values.viewState.spelLoadBalancers || [])
        .map(stringToOption);

      const vpcLoadBalancerOptions = (values.backingData.filtered.vpcLoadBalancers || []).map(stringToOption);

      const hasVpcLoadBalancers = values.vpcLoadBalancers && values.vpcLoadBalancers.length > 0;
      loadBalancersSection = (
        <>
          {dirty.loadBalancers && (
            <div className="col-md-12">
              <div className="alert alert-warning">
                <p>
                  <i className="fa fa-exclamation-triangle" />
                  The following load balancers could not be found in the selected account/region/VPC and were removed:
                </p>
                <ul>
                  {dirty.loadBalancers.map((lb) => (
                    <li key={lb}>{lb}</li>
                  ))}
                </ul>
                <p className="text-right">
                  <a
                    className="btn btn-sm btn-default dirty-flag-dismiss clickable"
                    onClick={() => this.clearWarnings('loadBalancers')}
                  >
                    Okay
                  </a>
                </p>
              </div>
            </div>
          )}
          <div className="form-group">
            <div className="col-md-4 sm-label-right">
              <b>Classic Load Balancers </b>
              <HelpField id="aws.loadBalancer.loadBalancers" />
            </div>
            <div className="col-md-7">
              {loadBalancerOptions.length === 0 && (
                <div className="form-control-static">No load balancers found in the selected account/region/VPC</div>
              )}
              {loadBalancerOptions.length > 0 && (
                <TetheredSelect
                  multi={true}
                  options={loadBalancerOptions}
                  value={values.loadBalancers}
                  onChange={this.loadBalancersChanged}
                />
              )}
            </div>
          </div>
          {!values.vpcId && (
            <div className="form-group">
              {!hasVpcLoadBalancers && !showVpcLoadBalancers && (
                <div>
                  <div className="col-md-8 col-md-offset-3">
                    <a className="clickable" onClick={() => this.setState({ showVpcLoadBalancers: true })}>
                      Add VPC Load Balancers
                    </a>
                  </div>
                </div>
              )}
              {hasVpcLoadBalancers && (
                <div>
                  <div className="col-md-4 sm-label-right">
                    <b>VPC Load Balancers</b>
                  </div>
                  <div className="col-md-8">
                    {vpcLoadBalancerOptions.length > 0 && (
                      <TetheredSelect
                        multi={true}
                        options={vpcLoadBalancerOptions}
                        value={values.vpcLoadBalancers}
                        onChange={this.vpcLoadBalancersChanged}
                      />
                    )}
                  </div>
                </div>
              )}
            </div>
          )}
          {!refreshed && (
            <div className="form-group small" style={{ marginTop: '20px' }}>
              <div className="col-md-8 col-md-offset-4">
                {refreshing && (
                  <p>
                    <span className="fa fa-sync-alt fa-spin" />
                    <span> refreshing...</span>
                  </p>
                )}
                {!refreshing && (
                  <p>
                    If you are looking for a load balancer or target group from a different application, <br />
                    <a className="clickable" onClick={this.refreshLoadBalancers}>
                      click here
                    </a>{' '}
                    to load all load balancers.
                  </p>
                )}
              </div>
            </div>
          )}
        </>
      );
    }

    return (
      <div className="container-fluid form-horizontal">
        {targetGroupSection}
        {loadBalancersSection}
      </div>
    );
  }
}
