import React from 'react';

import type { IExecutionDetailsSectionProps, IStage, IStageConfigProps } from '@spinnaker/core';
import {
  AuthenticationService,
  BakeExecutionLabel,
  BakeryReader,
  ChecklistInput,
  ExecutionDetailsSection,
  ExecutionDetailsTasks,
  Markdown,
  Registry,
  SETTINGS,
  Spinner,
  StageConfigField,
  StageFailureMessage,
} from '@spinnaker/core';

import { AWSProviderSettings } from '../../../aws.settings';

const storeTypes = ['ebs', 'docker'];

interface IBaseOsOption {
  id: string;
  detailedDescription?: string;
  shortDescription?: string;
  displayName?: string;
  vmTypes?: string[];
}

interface IAwsBakeStageState {
  baseLabelOptions: string[];
  baseOsOptions: IBaseOsOption[];
  extendedAttributeKey: string;
  extendedAttributeValue: string;
  loading: boolean;
  loadError: boolean;
  regions: string[];
  roscoMode: boolean;
  showAdvancedOptions: boolean;
  vmTypes: string[];
}

function deleteEmptyProperties(stage: IStage): IStage {
  return Object.keys(stage).reduce((acc, key) => {
    if ((stage as any)[key] !== '') {
      (acc as any)[key] = (stage as any)[key];
    }
    return acc;
  }, {} as IStage);
}

function baseOsDescription(baseOsOption: IBaseOsOption): string {
  const baseOsName = baseOsOption?.displayName || baseOsOption?.id || '';
  return baseOsOption?.shortDescription ? `${baseOsName} (${baseOsOption.shortDescription})` : baseOsName;
}

function roscoMode(stage: any): boolean {
  return (
    SETTINGS.feature.roscoMode ||
    (typeof SETTINGS.feature.roscoSelector === 'function' && SETTINGS.feature.roscoSelector(stage))
  );
}

function showMigrationFields(pipeline: any): boolean {
  return pipeline?.migrationStatus !== 'Started';
}

function showDockerPreview(stage: any): boolean {
  return !!AWSProviderSettings.dockerBakeryDeprecated && stage.storeType === 'docker';
}

export class AwsBakeStageConfig extends React.Component<IStageConfigProps, IAwsBakeStageState> {
  private mounted = false;

  public state: IAwsBakeStageState = {
    baseLabelOptions: [],
    baseOsOptions: [],
    extendedAttributeKey: '',
    extendedAttributeValue: '',
    loading: true,
    loadError: false,
    regions: [],
    roscoMode: false,
    showAdvancedOptions: false,
    vmTypes: ['hvm', 'pv'],
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

    Promise.all([
      BakeryReader.getRegions('aws'),
      BakeryReader.getBaseOsOptions('aws'),
      BakeryReader.getBaseLabelOptions(),
    ])
      .then(([regions, baseOsOptions, baseLabelOptions]) => {
        if (!this.mounted) {
          return;
        }

        const baseOsOptionsList = baseOsOptions.baseImages || [];
        const nextStage = this.applyDefaults(stage, regions as string[], baseOsOptionsList, baseLabelOptions);
        this.replaceStage(nextStage);

        this.setState({
          baseLabelOptions,
          baseOsOptions: baseOsOptionsList,
          loading: false,
          loadError: false,
          regions: regions as string[],
          roscoMode: roscoMode(nextStage),
          showAdvancedOptions: this.showAdvanced(nextStage),
          vmTypes: this.computeVmTypes(baseOsOptionsList, nextStage.baseOs),
        });
      })
      .catch(() => {
        if (this.mounted) {
          this.setState({ loading: false, loadError: true });
        }
      });
  }

  private applyDefaults(
    stage: IStage,
    regions: string[],
    baseOsOptions: IBaseOsOption[],
    baseLabelOptions: string[],
  ): IStage {
    const nextStage: any = deleteEmptyProperties({ ...stage });
    nextStage.extendedAttributes = nextStage.extendedAttributes || {};
    nextStage.regions = (nextStage.regions && [...nextStage.regions].sort()) || [];

    if (!nextStage.user) {
      nextStage.user = AuthenticationService.getAuthenticatedUser()?.name;
    }
    if (!nextStage.storeType && storeTypes.length) {
      nextStage.storeType = storeTypes[0];
    }
    if (regions.length === 1) {
      nextStage.region = regions[0];
    } else if (nextStage.region && !regions.includes(nextStage.region)) {
      delete nextStage.region;
    }
    if (!nextStage.regions.length && this.props.application.defaultRegions.aws) {
      nextStage.regions = [this.props.application.defaultRegions.aws];
    }
    let baseOsOptionsWithCustom = baseOsOptions;
    if (!nextStage.baseOs && baseOsOptions.length) {
      nextStage.baseOs = baseOsOptions[0].id;
    } else if (nextStage.baseOs && !baseOsOptions.find((baseOs) => baseOs.id === nextStage.baseOs)) {
      baseOsOptionsWithCustom = [
        ...baseOsOptions,
        { id: nextStage.baseOs, detailedDescription: 'Custom', vmTypes: ['hvm', 'pv'] },
      ];
    }
    if (!nextStage.baseLabel && baseLabelOptions.length) {
      nextStage.baseLabel = baseLabelOptions[0];
    }
    const vmTypes = this.computeVmTypes(baseOsOptionsWithCustom, nextStage.baseOs);
    if (!nextStage.vmType && vmTypes.length) {
      nextStage.vmType = vmTypes[0];
    }

    return nextStage;
  }

  private computeVmTypes(baseOsOptions: IBaseOsOption[], baseOs: string): string[] {
    if (baseOsOptions.length && baseOsOptions.every((option) => option.vmTypes)) {
      const match = baseOsOptions.find((option) => option.id === baseOs);
      return match?.vmTypes || ['hvm', 'pv'];
    }
    return ['hvm', 'pv'];
  }

  private showAdvanced(stage: any): boolean {
    return !!(
      stage.templateFileName ||
      (stage.extendedAttributes && Object.keys(stage.extendedAttributes).length > 0) ||
      stage.varFileName ||
      stage.baseName ||
      stage.baseAmi ||
      stage.amiName ||
      stage.amiSuffix ||
      stage.rootVolumeSize
    );
  }

  private replaceStage(nextStage: IStage): void {
    if (this.props.updateStage) {
      this.props.updateStage(nextStage);
    } else {
      this.props.updateStageField(nextStage);
    }
  }

  private updateStage(changes: any): void {
    const nextStage: any = deleteEmptyProperties({ ...this.props.stage, ...changes });
    this.replaceStage(nextStage);
    this.setState({ roscoMode: roscoMode(nextStage) });

    if (changes.baseOs !== undefined) {
      const vmTypes = this.computeVmTypes(this.state.baseOsOptions, changes.baseOs);
      this.setState({ vmTypes });
      if (vmTypes.length && !vmTypes.includes(nextStage.vmType)) {
        this.updateStage({ vmType: vmTypes[0] });
      }
    }
  }

  private updateExtendedAttribute(key: string, value: string): void {
    this.updateStage({ extendedAttributes: { ...((this.props.stage as any).extendedAttributes || {}), [key]: value } });
  }

  private removeExtendedAttribute(key: string): void {
    const extendedAttributes = { ...((this.props.stage as any).extendedAttributes || {}) };
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

  private renderDockerPreview(): React.ReactNode {
    const stage = this.props.stage as any;
    const showStoreTypeSelector = !this.state.roscoMode && showMigrationFields(this.props.pipeline);

    return (
      <>
        {AWSProviderSettings.dockerBakeWarning && (
          <div className="alert alert-warning sp-margin-s-top horizontal middle">
            <i className="fa fa-exclamation-triangle" />
            <div className="sp-margin-s-left">
              <Markdown message={AWSProviderSettings.dockerBakeWarning} />
            </div>
          </div>
        )}
        <div className="alert alert-info horizontal middle">
          <i className="fa fa-info-circle" />
          <div className="sp-margin-s-left">
            <Markdown message="To edit bake configuration or view advanced options, migrate to EBS store type." />
          </div>
        </div>
        {showStoreTypeSelector && (
          <StageConfigField label="Store Type">
            {storeTypes.map((storeType) => (
              <label className="radio-inline" key={storeType}>
                <input
                  checked={stage.storeType === storeType}
                  name="storeType"
                  type="radio"
                  onChange={() => this.updateStage({ storeType })}
                />
                {storeType.toUpperCase()}
              </label>
            ))}
          </StageConfigField>
        )}
        <StageConfigField label="Regions">
          {(stage.regions || []).map((region: string) => (
            <span className="sp-margin-xs-right" key={region}>
              {region}
            </span>
          ))}
        </StageConfigField>
        <StageConfigField label="Skip Region Detection">{String(!!stage.skipRegionDetection)}</StageConfigField>
        <StageConfigField label="Repo Path">{stage.package}</StageConfigField>
        <StageConfigField label="Base OS">{stage.baseOs}</StageConfigField>
        <StageConfigField label="VM Type">{stage.vmType}</StageConfigField>
        <StageConfigField label="Store Type">{stage.storeType}</StageConfigField>
        <StageConfigField label="Base Label">{stage.baseLabel}</StageConfigField>
      </>
    );
  }

  public render() {
    if (this.state.loading) {
      return <Spinner />;
    }
    if (this.state.loadError) {
      return <div className="alert alert-danger">Unable to load bakery options.</div>;
    }

    const stage = this.props.stage as any;

    if (showDockerPreview(stage)) {
      return <div className="form-horizontal">{this.renderDockerPreview()}</div>;
    }

    const showMigration = showMigrationFields(this.props.pipeline);
    const showVmTypeSelector = this.state.vmTypes.length > 1 && showMigration;
    const showStoreType = !AWSProviderSettings.dockerBakeryDeprecated;
    const showStoreTypeSelector = !this.state.roscoMode && showMigration && showStoreType;
    const showRebake = this.state.roscoMode || stage.rebake;
    const showAdvancedFields = this.state.showAdvancedOptions;
    const extendedAttributes = stage.extendedAttributes || {};
    const packageLabel = stage.storeType === 'docker' ? 'Repo Path' : 'Package';

    return (
      <div className="form-horizontal">
        {AWSProviderSettings.bakeWarning && (
          <div className="alert alert-warning sp-margin-s-top horizontal middle">
            <i className="fa fa-exclamation-triangle" />
            <div className="sp-margin-s-left">
              <Markdown message={AWSProviderSettings.bakeWarning} />
            </div>
          </div>
        )}
        <StageConfigField label="Regions">
          <ChecklistInput
            inline={true}
            name="regions"
            onChange={(event: any) => this.updateStage({ regions: event.target.value })}
            showSelectAll={true}
            stringOptions={this.state.regions}
            value={stage.regions || []}
          />
        </StageConfigField>
        <StageConfigField label="Skip Region Detection" helpKey="pipeline.config.bake.skipRegionDetection">
          <div className="checkbox" style={{ marginBottom: 0 }}>
            <label>
              <input
                checked={!!stage.skipRegionDetection}
                type="checkbox"
                onChange={(event) => this.updateStage({ skipRegionDetection: event.target.checked })}
              />
              Only bake explicitly selected regions
            </label>
          </div>
        </StageConfigField>
        <StageConfigField label={packageLabel} helpKey="pipeline.config.bake.package">
          <input
            className="form-control input-sm"
            value={stage.package || ''}
            onChange={(event) => this.updateStage({ package: event.target.value })}
          />
        </StageConfigField>
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
        {showVmTypeSelector && (
          <StageConfigField label="VM Type">
            {this.state.vmTypes.map((vmType) => (
              <label className="radio-inline" key={vmType}>
                <input
                  checked={stage.vmType === vmType}
                  name="vmType"
                  type="radio"
                  onChange={() => this.updateStage({ vmType })}
                />
                {vmType.toUpperCase()}
              </label>
            ))}
          </StageConfigField>
        )}
        {showStoreTypeSelector && (
          <StageConfigField label="Store Type">
            {storeTypes.map((storeType) => (
              <label className="radio-inline" key={storeType}>
                <input
                  checked={stage.storeType === storeType}
                  name="storeType"
                  type="radio"
                  onChange={() => this.updateStage({ storeType })}
                />
                {storeType.toUpperCase()}
              </label>
            ))}
          </StageConfigField>
        )}
        <StageConfigField label="Base Label">
          {this.state.baseLabelOptions.map((baseLabel) => (
            <label className="radio-inline" key={baseLabel}>
              <input
                checked={stage.baseLabel === baseLabel}
                name="baseLabel"
                type="radio"
                onChange={() => this.updateStage({ baseLabel })}
              />
              {baseLabel}
            </label>
          ))}
        </StageConfigField>
        {showRebake && (
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
            {showMigration && (
              <StageConfigField label="Template File Name" helpKey="pipeline.config.bake.templateFileName">
                <input
                  className="form-control input-sm"
                  value={stage.templateFileName || ''}
                  onChange={(event) => this.updateStage({ templateFileName: event.target.value })}
                />
              </StageConfigField>
            )}
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
                          value={extendedAttributes[key]}
                          onChange={(event) => this.updateExtendedAttribute(key, event.target.value)}
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
                        type="button"
                        onClick={() => this.addExtendedAttribute()}
                      >
                        <span className="glyphicon glyphicon-plus-sign" /> Add Extended Attribute
                      </button>
                    </td>
                  </tr>
                </tfoot>
              </table>
            </StageConfigField>
            {showMigration && (
              <StageConfigField label="Var File Name" helpKey="pipeline.config.bake.varFileName">
                <input
                  className="form-control input-sm"
                  value={stage.varFileName || ''}
                  onChange={(event) => this.updateStage({ varFileName: event.target.value })}
                />
              </StageConfigField>
            )}
            {!!AWSProviderSettings.minRootVolumeSize && showMigration && (
              <StageConfigField label="Root Volume Size">
                <input
                  type="number"
                  className="form-control input-sm"
                  style={{ width: 80, display: 'inline-block' }}
                  min={AWSProviderSettings.minRootVolumeSize}
                  value={stage.rootVolumeSize || ''}
                  onChange={(event) => this.updateStage({ rootVolumeSize: event.target.value })}
                />{' '}
                GB <span className="small">(minimum: {AWSProviderSettings.minRootVolumeSize})</span>
              </StageConfigField>
            )}
            {stage.storeType !== 'docker' && showMigration && (
              <StageConfigField label="Base Name">
                <input
                  className="form-control input-sm"
                  value={stage.baseName || ''}
                  onChange={(event) => this.updateStage({ baseName: event.target.value })}
                />
              </StageConfigField>
            )}
            {stage.storeType !== 'docker' && showMigration && (
              <StageConfigField label="Base AMI" helpKey="pipeline.config.bake.baseAmi">
                <input
                  className="form-control input-sm"
                  value={stage.baseAmi || ''}
                  onChange={(event) => this.updateStage({ baseAmi: event.target.value })}
                />
              </StageConfigField>
            )}
            {showMigration && (
              <StageConfigField
                label={stage.storeType === 'docker' ? 'Docker Image Name' : 'AMI Name'}
                helpKey="pipeline.config.bake.amiName"
              >
                <input
                  className="form-control input-sm"
                  value={stage.amiName || ''}
                  onChange={(event) => this.updateStage({ amiName: event.target.value })}
                />
              </StageConfigField>
            )}
            {showMigration && (
              <StageConfigField
                label={stage.storeType === 'docker' ? 'Docker Image Prefix' : 'AMI Suffix'}
                helpKey="pipeline.config.bake.amiSuffix"
              >
                <input
                  className="form-control input-sm"
                  value={stage.amiSuffix || ''}
                  onChange={(event) => this.updateStage({ amiSuffix: event.target.value })}
                />
              </StageConfigField>
            )}
          </>
        )}
      </div>
    );
  }
}

export function AwsBakeExecutionDetails({ current, execution, name, stage }: IExecutionDetailsSectionProps) {
  const context = (stage as any).context || {};
  const isRoscoMode = roscoMode(context);
  const showRebake = isRoscoMode || (execution as any)?.trigger?.rebake || context.rebake;
  const bakeFailedNoError = context.status?.result === 'FAILURE' && !(stage as any).failureMessage;
  const urlTemplate =
    (isRoscoMode && SETTINGS.roscoDetailUrl ? SETTINGS.roscoDetailUrl : SETTINGS.bakeryDetailUrl) || '';
  const bakeryDetailUrl = urlTemplate
    .replace(/\{\{\s*context\.region\s*\}\}/g, context.region || '')
    .replace(/\{\{\s*context\.status\.resourceId\s*\}\}/g, context.status?.resourceId || '');

  return (
    <ExecutionDetailsSection name={name} current={current}>
      <div className="row">
        <div className="col-md-6">
          <dl className="dl-narrow dl-horizontal">
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
            <dt>VM Type</dt>
            <dd>{(context.vmType || '').toUpperCase()}</dd>
            {!isRoscoMode && (
              <>
                <dt>Store Type</dt>
                <dd>{(context.storeType || '').toUpperCase()}</dd>
                <dt>Label</dt>
                <dd>{context.baseLabel}</dd>
              </>
            )}
            {showRebake && (
              <>
                <dt>Rebake</dt>
                <dd>{String((execution as any)?.trigger?.rebake || context.rebake || false)}</dd>
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
      <StageFailureMessage stage={stage as any} message={(stage as any).failureMessage} />
      {context.region && context.status?.resourceId && (
        <div className="row">
          <div className="col-md-12">
            <div className={`alert alert-${(stage as any).isFailed ? 'danger' : 'info'}`}>
              {context.previouslyBaked && <div>No changes detected; reused existing bake</div>}
              {context.imageName && (
                <div>
                  <strong>Image:</strong>
                  <div>{context.imageName}</div>
                  <div>({context.ami})</div>
                </div>
              )}
              {bakeFailedNoError && <span>Bake failed. </span>}
              <a target="_blank" rel="noopener noreferrer" href={bakeryDetailUrl}>
                View Bakery Details
              </a>
              {bakeFailedNoError && <span> for more info.</span>}
            </div>
          </div>
        </div>
      )}
    </ExecutionDetailsSection>
  );
}

(AwsBakeExecutionDetails as any).title = 'bakeConfig';

export const awsBakeStage = {
  key: 'bake',
  provides: 'bake',
  cloudProvider: 'aws',
  label: 'Bake',
  description: 'Bakes an image',
  component: AwsBakeStageConfig,
  executionDetailsSections: [AwsBakeExecutionDetails as any, ExecutionDetailsTasks],
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
        labels.map((label) => `<li>${label}</li>`).join('') +
        '</ul>' +
        'Otherwise, Spinnaker will bake and deploy the most-recently built package.',
    },
  ],
  restartable: true,
};

export function registerAwsBakeStage() {
  Registry.pipeline.registerStage(awsBakeStage);
}

registerAwsBakeStage();
