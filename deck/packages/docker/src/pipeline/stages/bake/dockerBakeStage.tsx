import { isEqual } from 'lodash';
import React from 'react';

import type {
  IExecutionDetailsSectionProps,
  IFormikStageConfigInjectedProps,
  IStage,
  IStageConfigProps,
} from '@spinnaker/core';
import {
  AuthenticationService,
  BakeExecutionLabel,
  BakeryReader,
  ExecutionDetailsSection,
  ExecutionDetailsTasks,
  FormikStageConfig,
  Registry,
  SETTINGS,
  Spinner,
  StageConfigField,
  StageFailureMessage,
} from '@spinnaker/core';

export const DOCKER_PIPELINE_STAGES_BAKE_DOCKERBAKESTAGE = 'spinnaker.docker.pipeline.stage.bakeStage';
export const name = DOCKER_PIPELINE_STAGES_BAKE_DOCKERBAKESTAGE; // for backwards compatibility

interface IBaseOsOption {
  id: string;
  shortDescription?: string;
  detailedDescription?: string;
  isImageFamily?: boolean;
  displayName?: string;
}

interface IDockerBakeStageDefaults {
  user: string;
  baseOsOptions: IBaseOsOption[];
  baseLabelOptions: string[];
}

interface IDockerBakeStageConfigState {
  baseLabelOptions: string[];
  baseOsOptions: IBaseOsOption[];
  loadError: boolean;
  loading: boolean;
}

function deleteEmptyProperties(stage: IStage): IStage {
  return Object.keys(stage).reduce((acc, key) => {
    if ((stage as any)[key] !== '') {
      (acc as any)[key] = (stage as any)[key];
    }
    return acc;
  }, {} as IStage);
}

export function applyDockerBakeStageDefaults(stage: IStage, defaults: IDockerBakeStageDefaults): IStage {
  const nextStage = deleteEmptyProperties({ ...stage });

  nextStage.region = nextStage.region || 'global';
  nextStage.user = nextStage.user || defaults.user;

  if (!nextStage.baseOs && defaults.baseOsOptions?.length) {
    nextStage.baseOs = defaults.baseOsOptions[0].id;
  }

  if (!nextStage.baseLabel && defaults.baseLabelOptions?.length) {
    nextStage.baseLabel = defaults.baseLabelOptions[0];
  }

  return nextStage;
}

function baseOsDescription(baseOsOption: IBaseOsOption): string {
  const baseOsName = baseOsOption?.displayName || baseOsOption?.id || '';
  return baseOsOption?.shortDescription ? `${baseOsName} (${baseOsOption.shortDescription})` : baseOsName;
}

function bakeryDetailUrl(stage: IStage): string {
  const context = stage.context || {};
  const urlTemplate = SETTINGS.bakeryDetailUrl || '';
  return urlTemplate
    .replace(/\{\{context\.region\}\}/g, context.region || '')
    .replace(/\{\{context\.status\.resourceId\}\}/g, context.status?.resourceId || '');
}

function DockerBakeStageForm({
  baseLabelOptions,
  baseOsOptions,
  formik,
}: IFormikStageConfigInjectedProps & IDockerBakeStageConfigState) {
  const stage = formik.values;
  const extendedAttributes = stage.extendedAttributes || {};
  const setFieldValue = (field: string) => (event: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    formik.setFieldValue(field, event.target.value);
  };

  return (
    <>
      <StageConfigField label="Package" helpKey="pipeline.config.bake.package">
        <input
          type="text"
          className="form-control input-sm"
          value={stage.package || ''}
          onChange={setFieldValue('package')}
        />
      </StageConfigField>
      <StageConfigField label="Organization" helpKey="pipeline.config.docker.bake.organization">
        <input
          type="text"
          className="form-control input-sm"
          value={stage.organization || ''}
          onChange={setFieldValue('organization')}
        />
      </StageConfigField>
      <StageConfigField label="Image Name" helpKey="pipeline.config.docker.bake.targetImage">
        <input
          type="text"
          className="form-control input-sm"
          value={stage.ami_name || ''}
          onChange={setFieldValue('ami_name')}
        />
      </StageConfigField>
      <StageConfigField label="Image tag" helpKey="pipeline.config.docker.bake.targetImageTag">
        <input
          type="text"
          className="form-control input-sm"
          value={extendedAttributes.docker_target_image_tag || ''}
          onChange={setFieldValue('extendedAttributes.docker_target_image_tag')}
        />
      </StageConfigField>
      <StageConfigField label="Base OS">
        <select className="form-control input-sm" value={stage.baseOs || ''} onChange={setFieldValue('baseOs')}>
          {baseOsOptions.map((baseOsOption: IBaseOsOption) => (
            <option key={baseOsOption.id} value={baseOsOption.id}>
              {baseOsDescription(baseOsOption)}
            </option>
          ))}
        </select>
      </StageConfigField>
      <StageConfigField label="Base Label">
        {baseLabelOptions.map((baseLabel: string) => (
          <label key={baseLabel} className="radio-inline">
            <input
              type="radio"
              checked={stage.baseLabel === baseLabel}
              onChange={() => formik.setFieldValue('baseLabel', baseLabel)}
            />
            {baseLabel}
          </label>
        ))}
      </StageConfigField>
      <StageConfigField label="Rebake">
        <div className="checkbox" style={{ marginBottom: 0 }}>
          <label>
            <input
              type="checkbox"
              checked={!!stage.rebake}
              onChange={(event) => formik.setFieldValue('rebake', event.target.checked)}
            />
            Rebake image without regard to the status of any existing bake
          </label>
        </div>
      </StageConfigField>
    </>
  );
}

export class DockerBakeStageConfig extends React.Component<IStageConfigProps, IDockerBakeStageConfigState> {
  private mounted = false;

  public state: IDockerBakeStageConfigState = {
    baseLabelOptions: [],
    baseOsOptions: [],
    loadError: false,
    loading: true,
  };

  public componentDidMount(): void {
    this.mounted = true;
    Promise.all([BakeryReader.getBaseOsOptions('docker'), BakeryReader.getBaseLabelOptions()])
      .then(([baseOsOptions, baseLabelOptions]) => {
        if (!this.mounted) {
          return;
        }

        const baseOsOptionsList = baseOsOptions.baseImages || [];
        const stageWithDefaults = applyDockerBakeStageDefaults(this.props.stage, {
          user: AuthenticationService.getAuthenticatedUser()?.name,
          baseOsOptions: baseOsOptionsList,
          baseLabelOptions,
        });

        if (!isEqual(stageWithDefaults, this.props.stage)) {
          this.props.updateStage(stageWithDefaults);
        }

        this.setState({
          baseLabelOptions,
          baseOsOptions: baseOsOptionsList,
          loadError: false,
          loading: false,
        });
      })
      .catch(() => {
        if (!this.mounted) {
          return;
        }

        this.setState({ loadError: true, loading: false });
      });
  }

  public componentWillUnmount(): void {
    this.mounted = false;
  }

  public render() {
    if (this.state.loading) {
      return <Spinner />;
    }

    if (this.state.loadError) {
      return <div className="alert alert-danger">Unable to load Docker bake options.</div>;
    }

    const authenticatedUser = AuthenticationService.getAuthenticatedUser();
    const stageWithDefaults = applyDockerBakeStageDefaults(this.props.stage, {
      user: authenticatedUser?.name,
      baseOsOptions: this.state.baseOsOptions,
      baseLabelOptions: this.state.baseLabelOptions,
    });

    return (
      <FormikStageConfig
        application={this.props.application}
        onChange={this.props.updateStage}
        pipeline={this.props.pipeline}
        stage={stageWithDefaults}
        render={(props: IFormikStageConfigInjectedProps) => <DockerBakeStageForm {...props} {...this.state} />}
      />
    );
  }
}

export function DockerBakeExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { current, name, stage } = props;
  const context = stage.context || {};
  const resourceId = context.status?.resourceId;

  return (
    <ExecutionDetailsSection name={name} current={current}>
      <div className="row">
        <div className="col-md-6">
          <dl className="dl-narrow dl-horizontal">
            <dt>Organization</dt>
            <dd>{context.organization}</dd>
            <dt>Image Name</dt>
            <dd>{context.ami_name}</dd>
            <dt>Image Tag</dt>
            <dd>{context.extendedAttributes?.docker_target_image_tag}</dd>
            <dt>Image</dt>
            <dd>{context.ami}</dd>
          </dl>
        </div>
        <div className="col-md-6">
          <dl className="dl-narrow dl-horizontal">
            <dt>Base OS</dt>
            <dd>{context.baseOs}</dd>
            <dt>Region</dt>
            <dd>{context.region}</dd>
            <dt>Package</dt>
            <dd>{context.package}</dd>
            <dt>Label</dt>
            <dd>{context.baseLabel}</dd>
          </dl>
        </div>
      </div>
      <StageFailureMessage stage={stage} message={stage.failureMessage} />
      {context.region && resourceId && (
        <div className="row">
          <div className="col-md-12">
            <div className={`alert alert-${stage.isFailed ? 'danger' : 'info'}`}>
              <a target="_blank" rel="noopener noreferrer" href={bakeryDetailUrl(stage)}>
                View Bakery Details
              </a>
            </div>
          </div>
        </div>
      )}
    </ExecutionDetailsSection>
  );
}

(DockerBakeExecutionDetails as any).title = 'bakeConfig';

export const DOCKER_BAKE_STAGE_CONFIG: any = {
  provides: 'bake',
  cloudProvider: 'docker',
  label: 'Bake',
  description: 'Bakes an image',
  component: DockerBakeStageConfig,
  executionDetailsSections: [DockerBakeExecutionDetails as any, ExecutionDetailsTasks],
  executionLabelComponent: BakeExecutionLabel,
  extraLabelLines: (stage: IStage) => {
    return (stage as any).masterStage.context.allPreviouslyBaked ||
      (stage as any).masterStage.context.somePreviouslyBaked
      ? 1
      : 0;
  },
  supportsCustomTimeout: true,
  validators: [{ type: 'requiredField', fieldName: 'package' }],
  restartable: true,
};

Registry.pipeline.registerStage(DOCKER_BAKE_STAGE_CONFIG);
