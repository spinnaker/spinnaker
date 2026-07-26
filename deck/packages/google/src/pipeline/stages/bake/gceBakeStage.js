import React from 'react';

import {
  ArtifactReferenceService,
  AuthenticationService,
  BakeExecutionLabel,
  BakeryReader,
  ExecutionArtifactTab,
  ExecutionDetailsSection,
  ExecutionDetailsTasks,
  ExpectedArtifactService,
  MapEditor,
  Registry,
  SETTINGS,
  StageConfigField,
  StageFailureMessage,
} from '@spinnaker/core';

const h = React.createElement;

export const GOOGLE_PIPELINE_STAGES_BAKE_GCEBAKESTAGE = 'spinnaker.gce.pipeline.stage..bakeStage';
export const name = GOOGLE_PIPELINE_STAGES_BAKE_GCEBAKESTAGE;

function updateStage(stage, updateStage, changes) {
  updateStage({ ...stage, ...changes });
}

function showAdvanced(stage) {
  return !!(
    stage.templateFileName ||
    (stage.extendedAttributes && Object.keys(stage.extendedAttributes).length) ||
    stage.varFileName ||
    stage.baseAmi ||
    stage.accountName
  );
}

class GceBakeStageConfig extends React.Component {
  state = {
    baseLabelOptions: [],
    baseOsOptions: [],
    roscoMode: false,
    showAdvancedOptions: showAdvanced(this.props.stage),
  };

  componentDidMount() {
    const { stage, updateStage: update } = this.props;
    stage.extendedAttributes = stage.extendedAttributes || {};
    stage.region = 'global';
    stage.cloudProvider = stage.cloudProvider || 'gce';
    stage.user = stage.user || AuthenticationService.getAuthenticatedUser().name;

    Promise.all([BakeryReader.getBaseOsOptions('gce'), BakeryReader.getBaseLabelOptions()]).then(
      ([baseOsOptions, baseLabelOptions]) => {
        const baseImages = baseOsOptions?.baseImages || [];
        const changes = {};
        if (!stage.baseOs && baseImages.length) {
          changes.baseOs = baseImages[0].id;
        }
        if (!stage.baseLabel && baseLabelOptions?.length) {
          changes.baseLabel = baseLabelOptions[0];
        }
        if (Object.keys(changes).length) {
          update({ ...stage, ...changes });
        }
        this.setState({
          baseLabelOptions: baseLabelOptions || [],
          baseOsOptions: baseImages,
          roscoMode:
            SETTINGS.feature.roscoMode ||
            (typeof SETTINGS.feature.roscoSelector === 'function' && SETTINGS.feature.roscoSelector(stage)),
          showAdvancedOptions: showAdvanced({ ...stage, ...changes }),
        });
      },
    );
  }

  renderArtifactSelector() {
    const { pipeline, stage, updateStage: update } = this.props;
    const expectedArtifacts = ExpectedArtifactService.getExpectedArtifactsAvailableToStage(stage, pipeline);
    const packageArtifactIds = stage.packageArtifactIds || [];

    return h(
      StageConfigField,
      { label: 'Package Artifacts' },
      h(
        'select',
        {
          className: 'form-control input-sm',
          multiple: true,
          value: packageArtifactIds,
          onChange: (e) => {
            const selected = Array.from(e.target.selectedOptions).map((option) => option.value);
            updateStage(stage, update, { packageArtifactIds: selected });
          },
        },
        expectedArtifacts.map((artifact) =>
          h('option', { key: artifact.id, value: artifact.id }, artifact.displayName || artifact.id),
        ),
      ),
    );
  }

  renderAdvancedOptions() {
    const { stage, updateStage: update } = this.props;
    if (!this.state.showAdvancedOptions) {
      return null;
    }

    return h(
      React.Fragment,
      null,
      h(
        StageConfigField,
        { label: 'Template File Name' },
        h('input', {
          className: 'form-control input-sm',
          value: stage.templateFileName || '',
          onChange: (e) => updateStage(stage, update, { templateFileName: e.target.value }),
        }),
      ),
      h(
        StageConfigField,
        { label: 'Account Name' },
        h('input', {
          className: 'form-control input-sm',
          value: stage.accountName || '',
          onChange: (e) => updateStage(stage, update, { accountName: e.target.value }),
        }),
      ),
      h(
        StageConfigField,
        { label: 'Extended Attributes' },
        h(MapEditor, {
          allowEmpty: true,
          model: stage.extendedAttributes || {},
          onChange: (extendedAttributes) => updateStage(stage, update, { extendedAttributes }),
        }),
      ),
      h(
        StageConfigField,
        { label: 'Var File Name' },
        h('input', {
          className: 'form-control input-sm',
          value: stage.varFileName || '',
          onChange: (e) => updateStage(stage, update, { varFileName: e.target.value }),
        }),
      ),
      h(
        StageConfigField,
        { label: 'Base Image' },
        h('input', {
          className: 'form-control input-sm',
          value: stage.baseAmi || '',
          onChange: (e) => updateStage(stage, update, { baseAmi: e.target.value }),
        }),
      ),
    );
  }

  render() {
    const { stage, updateStage: update } = this.props;
    stage.extendedAttributes = stage.extendedAttributes || {};
    stage.region = 'global';
    stage.cloudProvider = stage.cloudProvider || 'gce';
    stage.user = stage.user || AuthenticationService.getAuthenticatedUser().name;

    return h(
      'div',
      null,
      h(
        StageConfigField,
        { label: 'Package' },
        h('input', {
          className: 'form-control input-sm',
          value: stage.package || '',
          onChange: (e) => updateStage(stage, update, { package: e.target.value }),
        }),
      ),
      this.renderArtifactSelector(),
      h('hr'),
      h(
        StageConfigField,
        { label: 'Base OS' },
        h(
          'select',
          {
            className: 'form-control input-sm',
            value: stage.baseOs || '',
            onChange: (e) => updateStage(stage, update, { baseOs: e.target.value }),
          },
          this.state.baseOsOptions.map((option) =>
            h(
              'option',
              { key: option.id, value: option.id },
              option.displayName || option.shortDescription || option.id,
            ),
          ),
        ),
      ),
      h(
        StageConfigField,
        { label: 'Base Label' },
        this.state.baseLabelOptions.map((baseLabel) =>
          h(
            'label',
            { className: 'radio-inline', key: baseLabel },
            h('input', {
              checked: stage.baseLabel === baseLabel,
              type: 'radio',
              onChange: () => updateStage(stage, update, { baseLabel }),
            }),
            ` ${baseLabel}`,
          ),
        ),
      ),
      h('hr'),
      (this.state.roscoMode || stage.rebake) &&
        h(
          StageConfigField,
          { label: 'Rebake' },
          h(
            'div',
            { className: 'checkbox', style: { marginBottom: 0 } },
            h(
              'label',
              null,
              h('input', {
                checked: !!stage.rebake,
                type: 'checkbox',
                onChange: (e) => updateStage(stage, update, { rebake: e.target.checked }),
              }),
              ' Rebake image without regard to the status of any existing bake',
            ),
          ),
        ),
      h(
        'div',
        { className: 'form-group' },
        h(
          'div',
          { className: 'col-md-9 col-md-offset-1' },
          h(
            'div',
            { className: 'checkbox' },
            h(
              'label',
              null,
              h('input', {
                checked: this.state.showAdvancedOptions,
                type: 'checkbox',
                onChange: (e) => this.setState({ showAdvancedOptions: e.target.checked }),
              }),
              h('strong', null, ' Show Advanced Options'),
            ),
          ),
        ),
      ),
      this.renderAdvancedOptions(),
    );
  }
}

function valueForPath(source, path) {
  const value = path.split('.').reduce((acc, key) => acc?.[key], source);
  return value == null ? '' : String(value);
}

export function interpolatedBakeDetailUrl(stage) {
  const context = stage.context || {};
  const roscoMode =
    SETTINGS.feature.roscoMode ||
    (typeof SETTINGS.feature.roscoSelector === 'function' && SETTINGS.feature.roscoSelector(context));
  const template = (roscoMode && SETTINGS.roscoDetailUrl ? SETTINGS.roscoDetailUrl : SETTINGS.bakeryDetailUrl) || '';
  const source = { context };

  return template.replace(/\{\{\s*([^}]+?)\s*\}\}/g, (_match, path) => valueForPath(source, path));
}

function GceBakeExecutionDetails(props) {
  const { current, execution, stage } = props;
  const context = stage.context || {};
  const roscoMode =
    SETTINGS.feature.roscoMode ||
    (typeof SETTINGS.feature.roscoSelector === 'function' && SETTINGS.feature.roscoSelector(context));
  const bakeDetailUrl = interpolatedBakeDetailUrl(stage);

  return h(
    ExecutionDetailsSection,
    { name: props.name, current },
    h(
      'div',
      { className: 'row' },
      h(
        'div',
        { className: 'col-md-6' },
        h(
          'dl',
          { className: 'dl-narrow dl-horizontal' },
          h('dt', null, 'Image'),
          h('dd', null, context.ami),
          h('dt', null, 'Region'),
          h('dd', null, context.region),
          h('dt', null, 'Package'),
          h('dd', null, context.package),
        ),
      ),
      h(
        'div',
        { className: 'col-md-6' },
        h(
          'dl',
          { className: 'dl-narrow dl-horizontal' },
          h('dt', null, 'Base OS'),
          h('dd', null, context.baseOs),
          h('dt', null, 'Label'),
          h('dd', null, context.baseLabel),
          (roscoMode || execution.trigger?.rebake || context.rebake) && h('dt', null, 'Rebake'),
          (roscoMode || execution.trigger?.rebake || context.rebake) &&
            h('dd', null, String(execution.trigger?.rebake || context.rebake || false)),
          context.templateFileName && h('dt', null, 'Template'),
          context.templateFileName && h('dd', null, context.templateFileName),
          context.accountName && h('dt', null, 'Account Name'),
          context.accountName && h('dd', null, context.accountName),
          context.varFileName && h('dt', null, 'Var File'),
          context.varFileName && h('dd', null, context.varFileName),
        ),
      ),
    ),
    h(StageFailureMessage, { stage, message: stage.failureMessage }),
    context.region &&
      context.status?.resourceId &&
      h(
        'div',
        { className: 'row' },
        h(
          'div',
          { className: 'col-md-12' },
          h(
            'div',
            { className: `alert alert-${stage.isFailed ? 'danger' : 'info'}` },
            context.previouslyBaked && h('div', null, 'No changes detected; reused existing bake'),
            bakeDetailUrl &&
              h('a', { target: '_blank', rel: 'noopener noreferrer', href: bakeDetailUrl }, 'View Bakery Details'),
            context.imageName &&
              h('div', null, h('strong', null, 'Image:'), h('div', { className: 'break-word' }, context.imageName)),
          ),
        ),
      ),
  );
}
GceBakeExecutionDetails.title = 'bakeConfig';

export function registerGceBakeStage() {
  Registry.pipeline.registerStage({
    provides: 'bake',
    cloudProvider: 'gce',
    label: 'Bake',
    description: 'Bakes an image',
    component: GceBakeStageConfig,
    executionDetailsSections: [GceBakeExecutionDetails, ExecutionDetailsTasks, ExecutionArtifactTab],
    executionLabelComponent: BakeExecutionLabel,
    extraLabelLines: (stage) => {
      return stage.masterStage.context.allPreviouslyBaked || stage.masterStage.context.somePreviouslyBaked ? 1 : 0;
    },
    producesArtifacts: true,
    supportsCustomTimeout: true,
    validators: [
      {
        type: 'anyFieldRequired',
        fields: [
          { fieldName: 'package', fieldLabel: 'Package' },
          { fieldName: 'packageArtifactIds', fieldLabel: 'Package Artifacts' },
        ],
      },
    ],
    restartable: true,
    artifactExtractor: ExpectedArtifactService.accumulateArtifacts(['packageArtifactIds']),
    artifactRemover: ArtifactReferenceService.removeArtifactFromFields(['packageArtifactIds']),
  });
}
