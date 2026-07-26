import React from 'react';

import {
  AccountService,
  ExecutionDetailsSection,
  ExecutionDetailsTasks,
  MapEditor,
  PipelineConfigService,
  Registry,
  StageConfigField,
  StageConstants,
  StageFailureMessage,
} from '@spinnaker/core';

const h = React.createElement;

export const GOOGLE_PIPELINE_STAGES_TAGIMAGE_GCETAGIMAGESTAGE = 'spinnaker.gce.pipeline.stage..tagImageStage';
export const name = GOOGLE_PIPELINE_STAGES_TAGIMAGE_GCETAGIMAGESTAGE;

function selectOptions(accounts) {
  return accounts.map((account) => h('option', { key: account.name, value: account.name }, account.name));
}

function toggleConsideredStage(stage, refId, checked) {
  const current = stage.consideredStages || [];
  if (checked && !current.includes(refId)) {
    return current.concat(refId);
  }
  if (!checked) {
    return current.filter((candidate) => candidate !== refId);
  }
  return current;
}

class GceTagImageStageConfig extends React.Component {
  state = { accounts: [] };

  componentDidMount() {
    AccountService.listAccounts('gce').then((accounts) => this.setState({ accounts }));
  }

  render() {
    const { pipeline, stage, updateStage } = this.props;
    stage.tags = stage.tags || {};
    stage.cloudProvider = stage.cloudProvider || 'gce';

    const imageProducingStages = pipeline
      ? PipelineConfigService.getAllUpstreamDependencies(pipeline, stage).filter((upstreamStage) =>
          StageConstants.IMAGE_PRODUCING_STAGES.includes(upstreamStage.type),
        )
      : [];

    return h(
      'div',
      { className: 'form-horizontal' },
      h(
        StageConfigField,
        { label: 'Account' },
        h(
          'select',
          {
            className: 'form-control input-sm',
            required: true,
            value: stage.credentials || '',
            onChange: (e) => updateStage({ ...stage, credentials: e.target.value }),
          },
          h('option', { value: '' }, 'Select...'),
          selectOptions(this.state.accounts),
        ),
      ),
      h(
        StageConfigField,
        { label: 'Tags' },
        h(MapEditor, {
          allowEmpty: true,
          model: stage.tags,
          onChange: (tags) => updateStage({ ...stage, tags }),
        }),
      ),
      h(
        StageConfigField,
        { label: 'Stages (optional)' },
        h(
          'div',
          { className: 'checkbox' },
          imageProducingStages.map((upstreamStage) =>
            h(
              'label',
              { key: upstreamStage.refId, style: { display: 'block' } },
              h('input', {
                checked: (stage.consideredStages || []).includes(upstreamStage.refId),
                type: 'checkbox',
                onChange: (e) =>
                  updateStage({
                    ...stage,
                    consideredStages: toggleConsideredStage(stage, upstreamStage.refId, e.target.checked),
                  }),
              }),
              ` ${upstreamStage.name || upstreamStage.refId}`,
            ),
          ),
        ),
      ),
    );
  }
}

function stageName(execution, refId) {
  const stage = execution?.stages?.find((candidate) => candidate.refId === refId);
  return stage?.name || refId;
}

function GceTagImageExecutionDetails(props) {
  const { current, execution, stage } = props;
  const context = stage.context || {};
  const targets = context.targets || [];
  const tags = Object.entries(context.tags || {}).filter(([, value]) => value !== null);
  const consideredStages = context.consideredStages || [];

  return h(
    ExecutionDetailsSection,
    { name: props.name, current },
    !!targets.length &&
      h(
        'div',
        { className: 'row' },
        h(
          'div',
          { className: 'col-md-12' },
          h(
            'dl',
            { className: 'dl-narrow dl-horizontal' },
            h('dt', null, 'Account'),
            h('dd', null, context.credentials),
            h('dt', null, 'Images'),
            targets.map((target) => h('dd', { key: target.imageName }, target.imageName)),
          ),
        ),
      ),
    h(
      'div',
      { className: 'row' },
      h(
        'div',
        { className: 'col-md-12' },
        h(
          'dl',
          { className: 'dl-narrow dl-horizontal' },
          h('dt', null, 'Tags'),
          tags.map(([key, value]) => h('dd', { key }, `${key} = ${value}`)),
          !!consideredStages.length && h('dt', null, 'Stages'),
          consideredStages.map((refId) => h('dd', { key: refId }, stageName(execution, refId))),
        ),
      ),
    ),
    h(StageFailureMessage, { stage, message: stage.failureMessage }),
  );
}
GceTagImageExecutionDetails.title = 'tagImageConfig';

export function registerGceTagImageStage() {
  Registry.pipeline.registerStage({
    provides: 'upsertImageTags',
    cloudProvider: 'gce',
    component: GceTagImageStageConfig,
    executionDetailsSections: [GceTagImageExecutionDetails, ExecutionDetailsTasks],
  });
}
