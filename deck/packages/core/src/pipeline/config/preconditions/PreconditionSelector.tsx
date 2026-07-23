import React from 'react';

import type { IAccount } from '../../../account/AccountService';
import { AccountService } from '../../../account/AccountService';
import type { Application } from '../../../application';
import { AppListExtractor } from '../../../application/listExtractor/AppListExtractor';
import type { IStage } from '../../../domain';
import { HelpField } from '../../../help';
import type { IPrecondition } from './preconditionTypes';
import { listPreconditionTypes } from './preconditionTypes';
import { StageStatusPreconditionConfig } from './types/stageStatus/StageStatusPreconditionConfig';
import { ScopeClusterSelector } from '../../../widgets/ScopeClusterSelector';

export interface IPreconditionSelectorProps {
  application: Application;
  onChange: (precondition: IPrecondition) => void;
  precondition: IPrecondition;
  strategy: boolean;
  upstreamStages: IStage[];
}

export interface IPreconditionSelectorState {
  accounts: IAccount[];
}

export class PreconditionSelector extends React.Component<IPreconditionSelectorProps, IPreconditionSelectorState> {
  private mounted = false;

  public state: IPreconditionSelectorState = {
    accounts: [],
  };

  public componentDidMount() {
    this.mounted = true;
    const normalized = this.normalizePrecondition(this.props.precondition);
    if (normalized !== this.props.precondition) {
      this.props.onChange(normalized);
    }

    AccountService.listAccounts('aws').then((accounts) => {
      if (this.mounted) {
        this.setState({ accounts });
      }
    });
  }

  public componentWillUnmount() {
    this.mounted = false;
  }

  private normalizePrecondition(precondition: IPrecondition): IPrecondition {
    if (precondition.type && precondition.context && precondition.failPipeline !== undefined) {
      return precondition;
    }

    return {
      ...precondition,
      context: precondition.context || {},
      failPipeline: precondition.failPipeline === undefined ? true : precondition.failPipeline,
      type: precondition.type || listPreconditionTypes()[0]?.key,
    };
  }

  private updateType = (event: React.ChangeEvent<HTMLSelectElement>) => {
    this.props.onChange({
      ...this.props.precondition,
      context: null,
      type: event.target.value,
    });
  };

  private updateContextField = (field: string, value: any) => {
    this.props.onChange({
      ...this.props.precondition,
      context: {
        ...(this.props.precondition.context || {}),
        [field]: value,
      },
    });
  };

  private updateContext = (context: any) => {
    this.props.onChange({
      ...this.props.precondition,
      context,
    });
  };

  private updatePreconditionField = (field: keyof IPrecondition, value: any) => {
    this.props.onChange({
      ...this.props.precondition,
      [field]: value,
    });
  };

  private updateClusterSizeAccount = (event: React.ChangeEvent<HTMLSelectElement>) => {
    const credentials = event.target.value;
    const account = this.state.accounts.find((candidate) => candidate.name === credentials);

    this.props.onChange({
      ...this.props.precondition,
      cloudProvider: account?.type || this.props.precondition.cloudProvider,
      context: {
        ...(this.props.precondition.context || {}),
        cluster: undefined,
        credentials,
        moniker: undefined,
      },
    });
  };

  private updateClusterSizeRegion = (event: React.ChangeEvent<HTMLInputElement>) => {
    const context = this.props.precondition.context || {};
    const currentRegions = context.regions || [];
    const region = event.target.value;
    const regions = event.target.checked
      ? currentRegions.concat(region)
      : currentRegions.filter((current: string) => current !== region);

    this.props.onChange({
      ...this.props.precondition,
      context: {
        ...context,
        cluster: undefined,
        moniker: undefined,
        regions,
      },
    });
  };

  private updateClusterSizeCluster = ({ clusterName: cluster }: { clusterName: string }) => {
    const context = this.props.precondition.context || {};
    const clusterFilter = AppListExtractor.monikerClusterNameFilter(cluster);
    const moniker = AppListExtractor.getMonikers([this.props.application], clusterFilter)[0];

    this.props.onChange({
      ...this.props.precondition,
      context: {
        ...context,
        cluster,
        moniker: moniker ? { ...moniker, sequence: undefined } : undefined,
      },
    });
  };

  private getClusterSizeRegions() {
    const context = this.props.precondition.context || {};
    const accountFilter = (cluster: any) =>
      context.credentials && cluster ? cluster.account === context.credentials : true;
    return AppListExtractor.getRegions([this.props.application], accountFilter);
  }

  private getClusterSizeClusters() {
    const context = this.props.precondition.context || {};
    const clusterFilter = AppListExtractor.clusterFilterForCredentialsAndRegion(context.credentials, context.regions);
    return AppListExtractor.getClusters([this.props.application], clusterFilter);
  }

  private renderClusterSizeFields() {
    const context = this.props.precondition.context || {};
    const accounts = this.state.accounts.filter((account) => account.type === 'aws');

    return (
      <>
        {!this.props.strategy && (
          <>
            <div className="form-group row">
              <div className="col-sm-3 sm-label-right">Account</div>
              <div className="col-sm-9">
                <select
                  className="form-control input-sm"
                  name="credentials"
                  value={context.credentials || ''}
                  onChange={this.updateClusterSizeAccount}
                  required={true}
                >
                  <option value="">Select...</option>
                  {accounts.map((account) => (
                    <option key={account.name} value={account.name}>
                      {account.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div className="form-group row">
              <div className="col-sm-3 sm-label-right">Regions</div>
              <div className="col-sm-9">
                {!context.credentials && <p className="form-control-static">(Select an Account)</p>}
                {context.credentials &&
                  this.getClusterSizeRegions().map((region) => (
                    <label key={region} className="checkbox-inline">
                      <input
                        type="checkbox"
                        name="regions"
                        value={region}
                        checked={(context.regions || []).includes(region)}
                        onChange={this.updateClusterSizeRegion}
                      />{' '}
                      {region}
                    </label>
                  ))}
              </div>
            </div>
            <div className="form-group row">
              <div className="col-sm-3 sm-label-right">Cluster</div>
              <div className="col-sm-9">
                <ScopeClusterSelector
                  clusters={this.getClusterSizeClusters()}
                  model={context.cluster || ''}
                  onChange={this.updateClusterSizeCluster}
                />
              </div>
            </div>
          </>
        )}
        <div className="form-group row">
          <div className="col-sm-3 sm-label-right">
            Expected Size <HelpField id="pipeline.config.checkPreconditions.expectedSize" />
          </div>
          <div className="col-sm-1">
            <select
              className="input-sm"
              name="comparison"
              value={context.comparison || ''}
              onChange={(event) => this.updateContextField('comparison', event.target.value)}
            >
              {['==', '<', '<=', '>=', '>'].map((comparison) => (
                <option key={comparison} value={comparison}>
                  {comparison}
                </option>
              ))}
            </select>
          </div>
          <div className="col-sm-3">
            <input
              className="form-control input-sm"
              min={0}
              name="expected"
              required={true}
              type="number"
              value={context.expected === undefined ? '' : context.expected}
              onChange={(event) =>
                this.updateContextField('expected', event.target.value === '' ? undefined : Number(event.target.value))
              }
            />
          </div>
        </div>
        <div className="form-group row">
          <div className="col-sm-3 sm-label-right">
            Fail Pipeline <HelpField id="pipeline.config.checkPreconditions.failPipeline" />
          </div>
          <div className="col-sm-9">
            <input
              type="checkbox"
              name="failPipeline"
              checked={!!this.props.precondition.failPipeline}
              onChange={(event) => this.updatePreconditionField('failPipeline', event.target.checked)}
            />
          </div>
        </div>
      </>
    );
  }

  private renderContextFields() {
    if (this.props.precondition.type !== 'expression') {
      if (this.props.precondition.type === 'clusterSize') {
        return this.renderClusterSizeFields();
      }

      if (this.props.precondition.type === 'stageStatus') {
        return (
          <StageStatusPreconditionConfig
            preconditionContext={this.props.precondition.context || {}}
            upstreamStages={this.props.upstreamStages}
            updatePreconditionContext={this.updateContext}
          />
        );
      }

      return null;
    }

    return (
      <>
        <div className="form-group row">
          <div className="col-sm-3 sm-label-right">Expression</div>
          <div className="col-sm-9">
            <textarea
              className="form-control input-sm"
              name="expression"
              rows={4}
              value={this.props.precondition.context?.expression || ''}
              onChange={(event) => this.updateContextField('expression', event.target.value)}
            />
          </div>
        </div>
        <div className="form-group row">
          <div className="col-sm-3 sm-label-right">Fail Pipeline</div>
          <div className="col-sm-9">
            <input
              type="checkbox"
              name="failPipeline"
              checked={!!this.props.precondition.failPipeline}
              onChange={(event) => this.updatePreconditionField('failPipeline', event.target.checked)}
            />
          </div>
        </div>
        <div className="form-group row">
          <div className="col-sm-3 sm-label-right">Failure Message</div>
          <div className="col-sm-9">
            <textarea
              className="form-control input-sm"
              name="failureMessage"
              rows={4}
              value={this.props.precondition.context?.failureMessage || ''}
              onChange={(event) => this.updateContextField('failureMessage', event.target.value)}
            />
          </div>
        </div>
      </>
    );
  }

  public render() {
    const preconditionTypes = listPreconditionTypes();
    const selectedType = this.props.precondition.type || preconditionTypes[0]?.key;

    return (
      <div className="precondition-selector">
        <div className="form-group row">
          <div className="col-sm-3 sm-label-right" id="type">
            Check
          </div>
          <div className="col-sm-9">
            <select className="input-sm" name="preconditionType" value={selectedType} onChange={this.updateType}>
              {preconditionTypes.map((type) => (
                <option key={type.key} value={type.key}>
                  {type.label}
                </option>
              ))}
            </select>
          </div>
        </div>
        {this.renderContextFields()}
      </div>
    );
  }
}
