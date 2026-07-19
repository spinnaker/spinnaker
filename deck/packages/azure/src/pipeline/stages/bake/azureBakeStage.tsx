import React from 'react';

import type { IExecutionDetailsSectionProps } from '@spinnaker/core';
import {
  AccountService,
  AuthenticationService,
  BakeExecutionLabel,
  BakeryReader,
  ExecutionDetailsSection,
  ExecutionDetailsTasks,
  Registry,
  SETTINGS,
  Spinner,
  StageConfigField,
  StageFailureMessage,
} from '@spinnaker/core';

import { AzureImageReader } from '../../../image/image.reader';

const osTypeOptions = ['linux', 'windows'];
const packageTypeOptions = ['DEB', 'RPM'];

interface IAzureBakeStageState {
  accounts: string[];
  baseLabelOptions: string[];
  baseOsOptions: any[];
  extendedAttributeKey: string;
  extendedAttributeValue: string;
  loading: boolean;
  loadError: boolean;
  managedImageOptions: Array<{ id: string; name: string; osType: string }>;
  regions: string[];
  roscoMode: boolean;
  showAdvancedOptions: boolean;
  sourceImageMode: 'default' | 'managed' | 'custom';
}

function deleteEmptyProperties(stage: any): any {
  return Object.keys(stage).reduce((acc, key) => {
    if (stage[key] !== '') {
      acc[key] = stage[key];
    }
    return acc;
  }, {} as any);
}

function baseOsDescription(baseOsOption: any): string {
  const baseOsName = baseOsOption?.displayName || baseOsOption?.id || '';
  return baseOsOption?.shortDescription ? `${baseOsName} (${baseOsOption.shortDescription})` : baseOsName;
}

function sourceImageMode(stage: any): 'default' | 'managed' | 'custom' {
  if (stage.managedImage != null) {
    return 'managed';
  }
  if (stage.publisher != null) {
    return 'custom';
  }
  return 'default';
}

export class AzureBakeStageConfig extends React.Component<any, IAzureBakeStageState> {
  private azureImageReader = new AzureImageReader();
  private mounted = false;

  public state: IAzureBakeStageState = {
    accounts: [],
    baseLabelOptions: [],
    baseOsOptions: [],
    extendedAttributeKey: '',
    extendedAttributeValue: '',
    loading: true,
    loadError: false,
    managedImageOptions: [],
    regions: [],
    roscoMode: false,
    showAdvancedOptions: false,
    sourceImageMode: sourceImageMode(this.props.stage),
  };

  public componentDidMount(): void {
    this.mounted = true;
    this.initialize();
  }

  public componentWillUnmount(): void {
    this.mounted = false;
  }

  private initialize(): void {
    const stage = this.props.stage;
    stage.extendedAttributes = stage.extendedAttributes || {};
    stage.regions = stage.regions || [];

    Promise.all([
      AccountService.getCredentialsKeyedByAccount('azure'),
      BakeryReader.getRegions('azure'),
      BakeryReader.getBaseOsOptions('azure'),
      BakeryReader.getBaseLabelOptions(),
    ])
      .then(([credentialsKeyedByAccount, regions, baseOsOptions, baseLabelOptions]) => {
        if (!this.mounted) {
          return;
        }

        const nextStage = this.applyDefaults(
          stage,
          regions as string[],
          baseOsOptions.baseImages || [],
          baseLabelOptions,
        );
        this.replaceStage(nextStage);

        const nextState = {
          accounts: Object.keys(credentialsKeyedByAccount),
          baseLabelOptions,
          baseOsOptions: baseOsOptions.baseImages || [],
          loading: false,
          loadError: false,
          regions: regions as string[],
          roscoMode: this.roscoMode(nextStage),
          showAdvancedOptions: this.showAdvanced(nextStage),
          sourceImageMode: sourceImageMode(nextStage),
        };
        this.setState(nextState);

        if (nextState.sourceImageMode === 'managed') {
          this.loadManagedImages(nextStage.account);
        }
      })
      .catch(() => {
        if (this.mounted) {
          this.setState({ loading: false, loadError: true });
        }
      });
  }

  private applyDefaults(stage: any, regions: string[], baseOsOptions: any[], baseLabelOptions: string[]): any {
    const nextStage = deleteEmptyProperties({ ...stage });
    nextStage.extendedAttributes = nextStage.extendedAttributes || {};
    nextStage.regions = nextStage.regions || [];

    if (!nextStage.user) {
      nextStage.user = AuthenticationService.getAuthenticatedUser()?.name;
    }
    if (regions.length === 1) {
      nextStage.region = regions[0];
    } else if (nextStage.region && !regions.includes(nextStage.region)) {
      delete nextStage.region;
    }
    if (!nextStage.regions.length && this.props.application.defaultRegions.azure) {
      nextStage.regions = [this.props.application.defaultRegions.azure];
    }
    if (!nextStage.baseLabel && baseLabelOptions.length) {
      nextStage.baseLabel = baseLabelOptions[0];
    }
    if (!nextStage.baseOs && baseOsOptions.length && sourceImageMode(nextStage) === 'default') {
      nextStage.baseOs = baseOsOptions[0].id;
    }

    return nextStage;
  }

  private replaceStage(nextStage: any): void {
    Object.keys(this.props.stage).forEach((key) => {
      if (!Object.prototype.hasOwnProperty.call(nextStage, key)) {
        delete this.props.stage[key];
      }
    });
    Object.assign(this.props.stage, nextStage);
    if (this.props.updateStage) {
      this.props.updateStage(nextStage);
    } else if (this.props.updateStageField) {
      this.props.updateStageField(nextStage);
    }
  }

  private updateStage(changes: any): void {
    const nextStage = deleteEmptyProperties({ ...this.props.stage, ...changes });
    this.replaceStage(nextStage);
    if (typeof SETTINGS.feature.roscoSelector === 'function') {
      this.setState({ roscoMode: this.roscoMode(nextStage) });
    }
  }

  private updateExtendedAttribute(key: string, value: string): void {
    this.updateStage({ extendedAttributes: { ...(this.props.stage.extendedAttributes || {}), [key]: value } });
  }

  private removeExtendedAttribute(key: string): void {
    const extendedAttributes = { ...(this.props.stage.extendedAttributes || {}) };
    delete extendedAttributes[key];
    this.updateStage({ extendedAttributes });
  }

  private addExtendedAttribute(): void {
    const key = this.state.extendedAttributeKey.trim();
    if (!key) {
      return;
    }

    this.updateExtendedAttribute(key, this.state.extendedAttributeValue);
    this.setState({ extendedAttributeKey: '', extendedAttributeValue: '', showAdvancedOptions: true });
  }

  private roscoMode(stage: any): boolean {
    return (
      SETTINGS.feature.roscoMode ||
      (typeof SETTINGS.feature.roscoSelector === 'function' && SETTINGS.feature.roscoSelector(stage))
    );
  }

  private showAdvanced(stage: any): boolean {
    return !!(
      stage.templateFileName ||
      (stage.extendedAttributes && Object.keys(stage.extendedAttributes).length > 0) ||
      stage.varFileName
    );
  }

  private loadManagedImages(account: string): void {
    this.azureImageReader
      .findImages({ provider: 'azure', managedImages: true, account })
      .then((images: any[]) => {
        if (!this.mounted || this.props.stage.account !== account) {
          return;
        }
        this.setState({
          managedImageOptions: images.map((image) => ({
            id: image.imageName,
            name: image.imageName,
            osType: image.ostype,
          })),
        });
      })
      .catch(() => {});
  }

  private loadAccountRegions(account: string): void {
    AccountService.getRegionsForAccount(account)
      .then((regions: any[]) => {
        if (this.mounted && this.props.stage.account === account) {
          this.setState({ regions: regions.map((region) => region.name) });
        }
      })
      .catch(() => {});
  }

  private setSourceImageMode(sourceImageModeValue: 'default' | 'managed' | 'custom'): void {
    this.setState({ sourceImageMode: sourceImageModeValue });

    if (sourceImageModeValue === 'default') {
      this.updateStage({
        managedImage: null,
        offer: null,
        osType: null,
        packageType: null,
        publisher: null,
        sku: null,
      });
      return;
    }

    if (sourceImageModeValue === 'managed') {
      this.setState({ managedImageOptions: [] });
      this.loadManagedImages(this.props.stage.account);
      this.updateStage({ baseOs: null, offer: null, osType: null, packageType: null, publisher: null, sku: null });
      return;
    }

    this.updateStage({ baseOs: null, managedImage: null, osType: null, packageType: null });
  }

  private onAccountChange(account: string): void {
    this.updateStage({ account, managedImage: null, osType: null, packageType: null });
    if (account) {
      this.loadAccountRegions(account);
    }
    if (this.state.sourceImageMode === 'managed') {
      this.setState({ managedImageOptions: [] });
      this.loadManagedImages(account);
    }
  }

  private onManagedImageChange(managedImage: string): void {
    const selectedManagedImage = this.state.managedImageOptions.find((option) => option.id === managedImage);
    this.updateStage({
      managedImage,
      osType: selectedManagedImage?.osType?.toLowerCase(),
      packageType: null,
    });
  }

  private renderSourceImageFields(): React.ReactNode {
    const stage = this.props.stage;

    if (this.state.sourceImageMode === 'managed') {
      return (
        <StageConfigField label="Managed Image">
          <select
            className="form-control input-sm"
            name="managedImage"
            value={stage.managedImage || ''}
            onChange={(event) => this.onManagedImageChange(event.target.value)}
          >
            <option value="" />
            {this.state.managedImageOptions.map((image) => (
              <option key={image.id} value={image.id}>
                {image.name}
              </option>
            ))}
          </select>
        </StageConfigField>
      );
    }

    if (this.state.sourceImageMode === 'custom') {
      return (
        <>
          <StageConfigField label="Publisher">
            <input
              className="form-control input-sm"
              value={stage.publisher || ''}
              onChange={(event) => this.updateStage({ publisher: event.target.value })}
            />
          </StageConfigField>
          <StageConfigField label="Offer">
            <input
              className="form-control input-sm"
              value={stage.offer || ''}
              onChange={(event) => this.updateStage({ offer: event.target.value })}
            />
          </StageConfigField>
          <StageConfigField label="SKU">
            <input
              className="form-control input-sm"
              value={stage.sku || ''}
              onChange={(event) => this.updateStage({ sku: event.target.value })}
            />
          </StageConfigField>
          <StageConfigField label="OS Type">
            {osTypeOptions.map((osType) => (
              <label className="radio-inline" key={osType}>
                <input
                  checked={stage.osType === osType}
                  name="osType"
                  type="radio"
                  value={osType}
                  onChange={() => this.updateStage({ osType, packageType: null })}
                />
                {osType}
              </label>
            ))}
          </StageConfigField>
        </>
      );
    }

    return (
      <StageConfigField label="Base OS">
        <select
          className="form-control input-sm"
          value={stage.baseOs || ''}
          onChange={(event) => this.updateStage({ baseOs: event.target.value })}
        >
          {this.state.baseOsOptions.map((baseOsOption) => (
            <option key={baseOsOption.id} value={baseOsOption.id}>
              {baseOsDescription(baseOsOption)}
            </option>
          ))}
        </select>
      </StageConfigField>
    );
  }

  public render() {
    if (this.state.loading) {
      return <Spinner />;
    }
    if (this.state.loadError) {
      return <div className="alert alert-danger">Unable to load Azure bake options.</div>;
    }

    const stage = this.props.stage;
    const extendedAttributes = stage.extendedAttributes || {};
    const showAdvancedFields = this.state.showAdvancedOptions || this.state.roscoMode;

    return (
      <div className="form-horizontal">
        <StageConfigField label="Account">
          <select
            className="form-control input-sm"
            name="account"
            value={stage.account || ''}
            onChange={(event) => this.onAccountChange(event.target.value)}
          >
            <option value="" />
            {this.state.accounts.map((account) => (
              <option key={account} value={account}>
                {account}
              </option>
            ))}
          </select>
        </StageConfigField>
        <StageConfigField label="Regions">
          {this.state.regions.map((region) => (
            <label className="checkbox-inline" key={region}>
              <input
                checked={(stage.regions || []).includes(region)}
                type="checkbox"
                onChange={(event) => {
                  const regions = new Set(stage.regions || []);
                  event.target.checked ? regions.add(region) : regions.delete(region);
                  this.updateStage({ region, regions: Array.from(regions) });
                }}
              />
              {region}
            </label>
          ))}
        </StageConfigField>
        <div className="panel panel-default">
          <div className="panel-heading">
            <label className="col-md-3 sm-label-right" />
            <div className="btn-group btn-group-xs" role="group" aria-label="Source Image">
              <button
                className={`btn btn-default ${this.state.sourceImageMode === 'default' ? 'active' : ''}`}
                onClick={() => this.setSourceImageMode('default')}
                type="button"
              >
                Default Images
              </button>
              <button
                className={`btn btn-default ${this.state.sourceImageMode === 'managed' ? 'active' : ''}`}
                onClick={() => this.setSourceImageMode('managed')}
                type="button"
              >
                Managed Images
              </button>
              <button
                className={`btn btn-default ${this.state.sourceImageMode === 'custom' ? 'active' : ''}`}
                onClick={() => this.setSourceImageMode('custom')}
                type="button"
              >
                Custom Image
              </button>
            </div>
          </div>
          <div className="panel-body">
            {this.renderSourceImageFields()}
            {stage.osType === 'linux' && (
              <StageConfigField label="Package Type">
                {packageTypeOptions.map((packageType) => (
                  <label className="radio-inline" key={packageType}>
                    <input
                      checked={stage.packageType === packageType}
                      name="packageType"
                      type="radio"
                      value={packageType}
                      onChange={() => this.updateStage({ packageType })}
                    />
                    {packageType}
                  </label>
                ))}
              </StageConfigField>
            )}
          </div>
        </div>
        <StageConfigField label="Package" helpKey="pipeline.config.bake.package">
          <input
            className="form-control input-sm"
            value={stage.package || ''}
            onChange={(event) => this.updateStage({ package: event.target.value })}
          />
        </StageConfigField>
        <StageConfigField label="Base Label">
          {this.state.baseLabelOptions.map((baseLabel) => (
            <label className="radio-inline" key={baseLabel}>
              <input
                checked={stage.baseLabel === baseLabel}
                name="baseLabel"
                type="radio"
                value={baseLabel}
                onChange={() => this.updateStage({ baseLabel })}
              />
              {baseLabel}
            </label>
          ))}
        </StageConfigField>
        {(this.state.roscoMode || stage.rebake) && (
          <StageConfigField label="Rebake">
            <div className="checkbox" style={{ marginBottom: 0 }}>
              <label>
                <input
                  checked={!!stage.rebake}
                  type="checkbox"
                  onChange={(event) => this.updateStage({ rebake: event.target.checked })}
                />
                Rebake image without regard to the status of any existing bake
              </label>
            </div>
          </StageConfigField>
        )}
        <StageConfigField label="Base Name">
          <input
            className="form-control input-sm"
            value={stage.baseName || ''}
            onChange={(event) => this.updateStage({ baseName: event.target.value })}
          />
        </StageConfigField>
        <div className="form-group">
          <div className="col-md-9 col-md-offset-1">
            <div className="checkbox">
              <label>
                <input
                  checked={this.state.showAdvancedOptions}
                  type="checkbox"
                  onChange={(event) => this.setState({ showAdvancedOptions: event.target.checked })}
                />
                <strong>Show Advanced Options</strong>
              </label>
            </div>
          </div>
        </div>
        {showAdvancedFields && (
          <>
            <StageConfigField label="Template File Name" helpKey="pipeline.config.bake.templateFileName">
              <input
                className="form-control input-sm"
                value={stage.templateFileName || ''}
                onChange={(event) => this.updateStage({ templateFileName: event.target.value })}
              />
            </StageConfigField>
            <StageConfigField label="Extended Attributes" helpKey="pipeline.config.bake.extendedAttributes">
              <table className="table table-condensed packed">
                <tbody>
                  {Object.keys(extendedAttributes).map((key) => (
                    <tr key={key}>
                      <td>
                        <strong className="small">{key}</strong>
                      </td>
                      <td>
                        <input
                          className="form-control input-sm"
                          value={extendedAttributes[key]}
                          onChange={(event) => this.updateExtendedAttribute(key, event.target.value)}
                        />
                      </td>
                      <td className="text-right">
                        <button
                          className="btn btn-link btn-sm"
                          onClick={() => this.removeExtendedAttribute(key)}
                          type="button"
                        >
                          Remove
                        </button>
                      </td>
                    </tr>
                  ))}
                  <tr>
                    <td>
                      <input
                        className="form-control input-sm"
                        placeholder="Key"
                        value={this.state.extendedAttributeKey}
                        onChange={(event) => this.setState({ extendedAttributeKey: event.target.value })}
                      />
                    </td>
                    <td>
                      <input
                        className="form-control input-sm"
                        placeholder="Value"
                        value={this.state.extendedAttributeValue}
                        onChange={(event) => this.setState({ extendedAttributeValue: event.target.value })}
                      />
                    </td>
                    <td className="text-right">
                      <button
                        className="btn btn-block btn-sm add-new"
                        onClick={() => this.addExtendedAttribute()}
                        type="button"
                      >
                        Add Extended Attribute
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </StageConfigField>
            <StageConfigField label="Var File Name" helpKey="pipeline.config.bake.varFileName">
              <input
                className="form-control input-sm"
                value={stage.varFileName || ''}
                onChange={(event) => this.updateStage({ varFileName: event.target.value })}
              />
            </StageConfigField>
          </>
        )}
      </div>
    );
  }
}

function valueForPath(source: any, path: string): string {
  const value = path.split('.').reduce((acc, key) => acc?.[key], source);
  return value == null ? '' : String(value);
}

function interpolatedBakeDetailUrl(stage: any): string {
  const context = stage.context || {};
  const roscoMode =
    SETTINGS.feature.roscoMode ||
    (typeof SETTINGS.feature.roscoSelector === 'function' && SETTINGS.feature.roscoSelector(context));
  const template = (roscoMode && SETTINGS.roscoDetailUrl ? SETTINGS.roscoDetailUrl : SETTINGS.bakeryDetailUrl) || '';
  const source = { context };

  return template.replace(/\{\{\s*([^}]+?)\s*\}\}/g, (_match: string, path: string) => valueForPath(source, path));
}

export function AzureBakeExecutionDetails({ current, execution, name, stage }: IExecutionDetailsSectionProps) {
  const context = stage.context || {};
  const resourceId = context.status?.resourceId;
  const roscoMode =
    SETTINGS.feature.roscoMode ||
    (typeof SETTINGS.feature.roscoSelector === 'function' && SETTINGS.feature.roscoSelector(context));
  const showRebake = roscoMode || execution?.trigger?.rebake || context.rebake;

  return (
    <ExecutionDetailsSection name={name} current={current}>
      <div className="row">
        <div className="col-md-6">
          <dl className="dl-narrow dl-horizontal">
            <dt>Provider</dt>
            <dd>Azure</dd>
            <dt>Image</dt>
            <dd>{context.ami}</dd>
            <dt>Region</dt>
            <dd>{context.region}</dd>
            <dt>Package</dt>
            <dd>{context.package}</dd>
          </dl>
        </div>
        <div className="col-md-6">
          <dl className="dl-narrow dl-horizontal">
            <dt>Base OS</dt>
            <dd>{context.baseOs}</dd>
            <dt>Label</dt>
            <dd>{context.baseLabel}</dd>
            {showRebake && (
              <>
                <dt>Rebake</dt>
                <dd>{String(execution?.trigger?.rebake || context.rebake || false)}</dd>
              </>
            )}
            {context.templateFileName && (
              <>
                <dt>Template</dt>
                <dd>{context.templateFileName}</dd>
              </>
            )}
            {context.varFileName && (
              <>
                <dt>Var File</dt>
                <dd>{context.varFileName}</dd>
              </>
            )}
          </dl>
        </div>
      </div>
      <StageFailureMessage stage={stage} message={stage.failureMessage} />
      {context.region && resourceId && (
        <div className="row">
          <div className="col-md-12">
            <div className={`alert alert-${stage.isFailed ? 'danger' : 'info'}`}>
              {context.previouslyBaked && <div>No changes detected; reused existing bake</div>}
              <a target="_blank" rel="noopener noreferrer" href={interpolatedBakeDetailUrl(stage)}>
                View Bakery Details
              </a>
            </div>
          </div>
        </div>
      )}
    </ExecutionDetailsSection>
  );
}

(AzureBakeExecutionDetails as any).title = 'bakeConfig';

export function registerAzureBakeStage() {
  Registry.pipeline.registerStage({
    key: 'bake',
    provides: 'bake',
    cloudProvider: 'azure',
    label: 'Bake',
    description: 'Bakes an image',
    component: AzureBakeStageConfig,
    executionDetailsSections: [AzureBakeExecutionDetails as any, ExecutionDetailsTasks],
    executionLabelComponent: BakeExecutionLabel,
    extraLabelLines: (stage: any) => {
      return stage.masterStage.context.allPreviouslyBaked || stage.masterStage.context.somePreviouslyBaked ? 1 : 0;
    },
    supportsCustomTimeout: true,
    validators: [
      { type: 'requiredField', fieldName: 'package' },
      { type: 'requiredField', fieldName: 'regions' },
      {
        type: 'upstreamVersionProvided',
        checkParentTriggers: true,
        getMessage: (labels: string[]) =>
          'Bake stages should always have a stage or trigger preceding them that provides version information: ' +
          '<ul>' +
          labels.map((label: string) => `<li>${label}</li>`).join('') +
          '</ul>' +
          'Otherwise, Spinnaker will bake and deploy the most-recently built package.',
      },
    ] as any,
    restartable: true,
  } as any);
}
