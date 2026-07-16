import React from 'react';

import {
  ExecutionDetailsSection,
  ExecutionDetailsTasks,
  MapEditor,
  Registry,
  StageConfigField,
  StageFailureMessage,
} from '@spinnaker/core';

const h = React.createElement;

export const GOOGLE_PIPELINE_STAGES_FINDIMAGEFROMTAGS_GCEFINDIMAGEFROMTAGSSTAGE =
  'spinnaker.gce.pipeline.stage..findImageFromTagsStage';
export const name = GOOGLE_PIPELINE_STAGES_FINDIMAGEFROMTAGS_GCEFINDIMAGEFROMTAGSSTAGE;

function GceFindImageFromTagsStageConfig({ stage, updateStage }) {
  stage.tags = stage.tags || {};
  stage.regions = stage.regions || [];
  stage.cloudProvider = stage.cloudProvider || 'gce';
  return h(
    'div',
    { className: 'form-horizontal' },
    h(
      StageConfigField,
      { label: 'Package' },
      h('input', {
        className: 'form-control input-sm',
        value: stage.packageName || '',
        onChange: (e) => updateStage({ ...stage, packageName: e.target.value }),
      }),
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
  );
}

function Tags({ tags, separator = ':' }) {
  const entries = Object.entries(tags || {});
  if (!entries.length) {
    return null;
  }

  return h(
    React.Fragment,
    null,
    entries.map(([key, value], index) =>
      h('span', { key }, `${key}${separator}${value}${index === entries.length - 1 ? '' : ', '}`),
    ),
  );
}

function GceFindImageFromTagsExecutionDetails(props) {
  const { current, stage } = props;
  const context = stage.context || {};
  const images = context.amiDetails || [];

  return h(
    ExecutionDetailsSection,
    { name: props.name, current },
    h(
      'div',
      { className: 'row' },
      h(
        'div',
        { className: 'col-md-12' },
        h(
          'dl',
          { className: 'dl-narrow dl-horizontal' },
          h('dt', null, 'Package'),
          h('dd', null, context.packageName),
          h('dt', null, 'Tags'),
          h('dd', null, h(Tags, { tags: context.tags })),
        ),
      ),
    ),
    h(StageFailureMessage, { stage, message: stage.failureMessage }),
    !!images.length &&
      h(
        'div',
        { className: 'row' },
        h(
          'div',
          { className: 'col-md-12' },
          h(
            'div',
            { className: 'well alert alert-info' },
            h('h4', null, 'Results'),
            images.map((image) =>
              h(
                'dl',
                { className: 'dl-narrow dl-horizontal', key: `${image.region}:${image.imageName}` },
                h('dt', null, 'Region'),
                h('dd', null, image.region),
                h('dt', null, 'Image'),
                h('dd', null, image.imageName),
              ),
            ),
          ),
        ),
      ),
  );
}
GceFindImageFromTagsExecutionDetails.title = 'findImageConfig';

export function registerGceFindImageFromTagsStage() {
  Registry.pipeline.registerStage({
    provides: 'findImageFromTags',
    cloudProvider: 'gce',
    component: GceFindImageFromTagsStageConfig,
    executionDetailsSections: [GceFindImageFromTagsExecutionDetails, ExecutionDetailsTasks],
    validators: [
      { type: 'requiredField', fieldName: 'packageName' },
      { type: 'requiredField', fieldName: 'tags' },
    ],
  });
}
