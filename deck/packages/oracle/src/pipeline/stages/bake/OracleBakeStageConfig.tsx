import React from 'react';

import type { IAccount, IStageConfigProps } from '@spinnaker/core';
import {
  AccountService,
  AuthenticationService,
  BakeExecutionLabel,
  BakeryReader,
  ExecutionDetailsTasks,
  HelpField,
  Registry,
  StageConfigField,
} from '@spinnaker/core';

interface IBaseOsOption {
  id: string;
  shortDescription?: string;
  detailedDescription?: string;
}

interface IOracleBakeStageState {
  accounts: IAccount[];
  baseOsOptions: IBaseOsOption[];
  extendedAttributeKey: string;
  extendedAttributeValue: string;
  loading: boolean;
}

function baseOsDescription(baseOsOption: IBaseOsOption): string {
  return baseOsOption.id + (baseOsOption.shortDescription ? ` (${baseOsOption.shortDescription})` : '');
}

export class OracleBakeStageConfig extends React.Component<IStageConfigProps, IOracleBakeStageState> {
  private mounted = false;

  public state: IOracleBakeStageState = {
    accounts: [],
    baseOsOptions: [],
    extendedAttributeKey: '',
    extendedAttributeValue: '',
    loading: true,
  };

  public componentDidMount(): void {
    this.mounted = true;
    const stage = this.props.stage as any;
    if (!stage.cloudProvider) {
      this.props.updateStageField({ cloudProvider: 'oracle', cloudProviderType: 'oracle' });
    }

    Promise.all([BakeryReader.getBaseOsOptions('oracle'), AccountService.listAccounts('oracle')]).then(
      ([baseOsOptions, accounts]) => {
        if (!this.mounted) {
          return;
        }

        const changes: Record<string, any> = {};
        if (!stage.user) {
          changes.user = AuthenticationService.getAuthenticatedUser()?.name;
        }
        if (!stage.upgrade) {
          changes.upgrade = true;
        }
        if (!stage.baseOs && baseOsOptions.baseImages.length > 0) {
          changes.baseOs = baseOsOptions.baseImages[0].id;
        }
        if (Object.keys(changes).length) {
          this.props.updateStageField(changes);
        }

        if (stage.accountName) {
          this.loadRegionForAccount(stage.accountName);
        }

        this.setState({ accounts, baseOsOptions: baseOsOptions.baseImages, loading: false });
      },
    );
  }

  public componentWillUnmount(): void {
    this.mounted = false;
  }

  private loadRegionForAccount(accountName: string): void {
    AccountService.getRegionsForAccount(accountName).then((regions) => {
      if (this.mounted && Array.isArray(regions) && regions.length) {
        this.props.updateStageField({ region: regions[0].name });
      }
    });
  }

  private accountChanged = (accountName: string): void => {
    this.props.updateStageField({ accountName });
    this.loadRegionForAccount(accountName);
  };

  private updateExtendedAttribute(key: string, value: string): void {
    const stage = this.props.stage as any;
    this.props.updateStageField({ extendedAttributes: { ...(stage.extendedAttributes || {}), [key]: value } });
  }

  private removeExtendedAttribute(key: string): void {
    const stage = this.props.stage as any;
    const extendedAttributes = { ...(stage.extendedAttributes || {}) };
    delete extendedAttributes[key];
    this.props.updateStageField({ extendedAttributes });
  }

  private addExtendedAttribute = (): void => {
    const key = this.state.extendedAttributeKey.trim();
    if (!key) {
      return;
    }
    this.updateExtendedAttribute(key, this.state.extendedAttributeValue);
    this.setState({ extendedAttributeKey: '', extendedAttributeValue: '' });
  };

  public render() {
    if (this.state.loading) {
      return <div className="horizontal center middle">Loading...</div>;
    }

    const stage = this.props.stage as any;
    const extendedAttributes = stage.extendedAttributes || {};

    return (
      <div className="form-horizontal">
        <StageConfigField label="Oracle Account" helpKey="oracle.pipeline.config.bake.account_name">
          <select
            className="form-control input-sm"
            onChange={(event) => this.accountChanged(event.target.value)}
            value={stage.accountName || ''}
          >
            <option value="" />
            {this.state.accounts.map((account) => (
              <option key={account.name} value={account.name}>
                {account.name}
              </option>
            ))}
          </select>
        </StageConfigField>
        <StageConfigField label="Region" helpKey="oracle.pipeline.config.bake.regions">
          <input className="form-control input-sm" readOnly={true} value={stage.region || ''} />
        </StageConfigField>
        <StageConfigField label="Base Image" helpKey="oracle.pipeline.config.bake.baseOsOption">
          {this.state.baseOsOptions.map((baseImage) => (
            <div className="radio" key={baseImage.id}>
              <label>
                <input
                  checked={stage.baseOs === baseImage.id}
                  onChange={() => this.props.updateStageField({ baseOs: baseImage.id })}
                  type="radio"
                />
                {baseOsDescription(baseImage)} <HelpField content={baseImage.detailedDescription} />
              </label>
            </div>
          ))}
        </StageConfigField>
        <StageConfigField label="Image Name" helpKey="oracle.pipeline.config.bake.image_name">
          <input
            className="form-control input-sm"
            onChange={(event) => this.props.updateStageField({ amiName: event.target.value })}
            value={stage.amiName || ''}
          />
        </StageConfigField>
        <StageConfigField label="Package" helpKey="oracle.pipeline.config.bake.package">
          <input
            className="form-control input-sm"
            onChange={(event) => this.props.updateStageField({ package: event.target.value })}
            value={stage.package || ''}
          />
        </StageConfigField>
        <StageConfigField label="Rebake" helpKey="execution.forceRebake">
          <div className="checkbox" style={{ marginBottom: 0 }}>
            <label>
              <input
                checked={!!stage.rebake}
                onChange={(event) => this.props.updateStageField({ rebake: event.target.checked })}
                type="checkbox"
              />
              Rebake image without regard to the status of any existing bake
            </label>
          </div>
        </StageConfigField>
        <div className="form-group">
          <div className="col-md-9 col-md-offset-1">
            <div className="checkbox">
              <label>
                <input
                  checked={!!stage.showAdvancedOptions}
                  onChange={(event) => this.props.updateStageField({ showAdvancedOptions: event.target.checked })}
                  type="checkbox"
                />
                <strong>Show Advanced Options</strong>
              </label>
            </div>
          </div>
        </div>
        {stage.showAdvancedOptions && (
          <>
            <StageConfigField label="Template File Name" helpKey="pipeline.config.bake.templateFileName">
              <input
                className="form-control input-sm"
                onChange={(event) => this.props.updateStageField({ templateFileName: event.target.value })}
                value={stage.templateFileName || ''}
              />
            </StageConfigField>
            <StageConfigField label="Var File Name" helpKey="pipeline.config.bake.varFileName">
              <input
                className="form-control input-sm"
                onChange={(event) => this.props.updateStageField({ varFileName: event.target.value })}
                value={stage.varFileName || ''}
              />
            </StageConfigField>
            <StageConfigField label="Extended Attributes" helpKey="pipeline.config.bake.extendedAttributes">
              <table className="table table-condensed packed">
                <thead>
                  <tr>
                    <th style={{ width: '40%' }}>Key</th>
                    <th style={{ width: '60%' }}>Value</th>
                    <th className="text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {Object.keys(extendedAttributes).map((key) => (
                    <tr key={key}>
                      <td>
                        <strong className="small">{key}</strong>
                      </td>
                      <td>
                        <input
                          className="form-control input-sm"
                          onChange={(event) => this.updateExtendedAttribute(key, event.target.value)}
                          value={extendedAttributes[key]}
                        />
                      </td>
                      <td className="text-right">
                        <a
                          className="small"
                          href=""
                          onClick={(event) => {
                            event.preventDefault();
                            this.removeExtendedAttribute(key);
                          }}
                        >
                          Remove
                        </a>
                      </td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr>
                    <td>
                      <input
                        className="form-control input-sm"
                        onChange={(event) => this.setState({ extendedAttributeKey: event.target.value })}
                        placeholder="Key"
                        value={this.state.extendedAttributeKey}
                      />
                    </td>
                    <td>
                      <input
                        className="form-control input-sm"
                        onChange={(event) => this.setState({ extendedAttributeValue: event.target.value })}
                        placeholder="Value"
                        value={this.state.extendedAttributeValue}
                      />
                    </td>
                    <td className="text-right">
                      <button
                        className="btn btn-block btn-sm add-new"
                        onClick={this.addExtendedAttribute}
                        type="button"
                      >
                        <span className="glyphicon glyphicon-plus-sign" /> Add Extended Attribute
                      </button>
                    </td>
                  </tr>
                </tfoot>
              </table>
            </StageConfigField>
            <StageConfigField label="Upgrade" helpKey="oracle.pipeline.config.bake.upgrade">
              <label className="checkbox-inline">
                <input
                  checked={!!stage.upgrade}
                  onChange={(event) => this.props.updateStageField({ upgrade: event.target.checked })}
                  type="checkbox"
                />
                Perform a package manager upgrade before proceeding with the package installation
              </label>
            </StageConfigField>
          </>
        )}
      </div>
    );
  }
}

export const oracleBakeStage = {
  key: 'bake',
  provides: 'bake',
  cloudProvider: 'oracle',
  label: 'Bake',
  description: 'Bakes an image',
  component: OracleBakeStageConfig,
  executionDetailsSections: [ExecutionDetailsTasks],
  executionLabelComponent: BakeExecutionLabel,
  supportsCustomTimeout: true,
  validators: [
    { type: 'requiredField', fieldName: 'accountName' },
    { type: 'requiredField', fieldName: 'region' },
    { type: 'requiredField', fieldName: 'baseOs' },
    { type: 'requiredField', fieldName: 'upgrade' },
    { type: 'requiredField', fieldName: 'cloudProviderType' },
    { type: 'requiredField', fieldName: 'amiName', fieldLabel: 'Image Name' },
  ],
  restartable: true,
};

export function registerOracleBakeStage() {
  Registry.pipeline.registerStage(oracleBakeStage);
}

registerOracleBakeStage();
