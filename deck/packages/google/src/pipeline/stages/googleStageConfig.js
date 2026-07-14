import React from 'react';

import {
  AccountService,
  DeploymentStrategySelector,
  NameUtils,
  NgReact,
  StageConfigField,
  StageConstants,
} from '@spinnaker/core';

const h = React.createElement;

export const gceSelectionStrategies = [
  {
    label: 'Largest',
    val: 'LARGEST',
    description: 'When multiple server groups exist, prefer the server group with the most instances',
  },
  { label: 'Newest', val: 'NEWEST', description: 'When multiple server groups exist, prefer the newest' },
  { label: 'Oldest', val: 'OLDEST', description: 'When multiple server groups exist, prefer the oldest' },
  { label: 'Fail', val: 'FAIL', description: 'When multiple server groups exist, fail' },
];

const scaleActions = [
  { label: 'Scale Up', val: 'scale_up' },
  { label: 'Scale Down', val: 'scale_down' },
  { label: 'Scale to Cluster Size', val: 'scale_to_cluster' },
  { label: 'Scale to Exact Size', val: 'scale_exact' },
];

const resizeTypes = [
  { label: 'Percentage', val: 'pct' },
  { label: 'Incremental', val: 'incr' },
];

function setDefault(stage, key, value) {
  if (stage[key] === undefined || stage[key] === null) {
    stage[key] = value;
  }
}

export function initializeGceStage(stage, application, defaults = {}) {
  setDefault(stage, 'cloudProvider', 'gce');
  setDefault(stage, 'cloudProviderType', 'gce');
  setDefault(stage, 'credentials', application?.defaultCredentials?.gce);
  stage.regions = stage.regions || [];
  if (!stage.regions.length && application?.defaultRegions?.gce) {
    stage.regions.push(application.defaultRegions.gce);
  }
  Object.keys(defaults).forEach((key) => setDefault(stage, key, defaults[key]));
  if (
    stage.isNew &&
    application?.attributes?.platformHealthOnlyShowOverride &&
    application.attributes.platformHealthOnly
  ) {
    stage.interestingHealthProviderNames = ['Google'];
  }
}

function replaceStage(props, stage) {
  props.updateStage({ ...stage });
}

function updateStage(props, changes) {
  replaceStage(props, { ...props.stage, ...changes });
}

function parseNumber(value) {
  return value === '' ? undefined : Number(value);
}

function pluralize(label, value) {
  return Number(value) === 1 ? label : `${label}s`;
}

function TextInput({ value, onChange, type = 'text', disabled = false }) {
  return h('input', {
    className: 'form-control input-sm',
    disabled,
    type,
    value: value ?? '',
    onChange: (e) => onChange(e.target.value),
  });
}

function NumberInput({ value, onChange, disabled = false, style = undefined }) {
  return h('input', {
    className: 'form-control input-sm',
    disabled,
    min: 0,
    style,
    type: 'number',
    value: value ?? '',
    onChange: (e) => onChange(parseNumber(e.target.value)),
  });
}

function SelectInput({ value, onChange, options }) {
  return h(
    'select',
    { className: 'form-control input-sm', value: value ?? '', onChange: (e) => onChange(e.target.value) },
    h('option', { value: '' }, 'Select...'),
    options.map((option) =>
      h('option', { key: option.val || option.name, value: option.val || option.name }, option.label || option.name),
    ),
  );
}

function InlineSelectInput({ value, onChange, options, style }) {
  return h(
    'select',
    { className: 'form-control input-sm', style, value: value ?? '', onChange: (e) => onChange(e.target.value) },
    options.map((option) => h('option', { key: option.val, value: option.val }, option.label)),
  );
}

function CapacityFields({ capacity = {}, disabled = false, onChange }) {
  const updateCapacity = (field, value) => onChange({ ...capacity, [field]: value });
  return h(
    React.Fragment,
    null,
    h(
      'div',
      { className: 'form-group' },
      h('div', { className: 'col-md-2 col-md-offset-3' }, 'Min'),
      h('div', { className: 'col-md-2' }, 'Max'),
      h('div', { className: 'col-md-2' }, 'Desired'),
    ),
    h(
      'div',
      { className: 'form-group' },
      h(
        'div',
        { className: 'col-md-2 col-md-offset-3' },
        h(NumberInput, { disabled, value: capacity.min, onChange: (value) => updateCapacity('min', value) }),
      ),
      h(
        'div',
        { className: 'col-md-2' },
        h(NumberInput, { disabled, value: capacity.max, onChange: (value) => updateCapacity('max', value) }),
      ),
      h(
        'div',
        { className: 'col-md-2' },
        h(NumberInput, { disabled, value: capacity.desired, onChange: (value) => updateCapacity('desired', value) }),
      ),
    ),
  );
}

function GcePlatformHealthOverride({ application, stage, updateStage: update }) {
  if (!application?.attributes?.platformHealthOnlyShowOverride) {
    return null;
  }

  const checked =
    Array.isArray(stage.interestingHealthProviderNames) && stage.interestingHealthProviderNames[0] === 'Google';

  return h(
    StageConfigField,
    { label: 'Task Completion' },
    h(
      'div',
      { className: 'checkbox' },
      h(
        'label',
        null,
        h('input', {
          checked,
          type: 'checkbox',
          onChange: (e) =>
            update({ ...stage, interestingHealthProviderNames: e.target.checked ? ['Google'] : undefined }),
        }),
        ' Consider only Google health',
      ),
    ),
  );
}

class GceBaseStageConfig extends React.Component {
  state = { accounts: [] };

  componentDidMount() {
    AccountService.listAccounts('gce').then((accounts) => this.setState({ accounts }));
  }

  renderAccountRegionClusterSelector(options = {}) {
    const { application, pipeline, stage } = this.props;
    if (pipeline?.strategy) {
      return null;
    }

    const { AccountRegionClusterSelector } = NgReact;
    return h(AccountRegionClusterSelector, {
      accounts: this.state.accounts,
      application,
      clusterField: options.clusterField,
      component: stage,
      singleRegion: options.singleRegion,
    });
  }

  renderTargetSelect(options = StageConstants.TARGET_LIST) {
    const { TargetSelect } = NgReact;
    return h(
      StageConfigField,
      { label: 'Target' },
      h(TargetSelect, { model: this.props.stage, options, onChange: (target) => updateStage(this.props, { target }) }),
    );
  }

  renderDeploymentStrategySelector() {
    return h(DeploymentStrategySelector, {
      command: this.props.stage,
      fieldColumns: '6',
      onFieldChange: (key, value) => updateStage(this.props, { [key]: value }),
      onStrategyChange: (command) => replaceStage(this.props, command),
    });
  }
}

export class GceTargetServerGroupStageConfig extends GceBaseStageConfig {
  render() {
    const { application, stage } = this.props;
    initializeGceStage(stage, application, { target: StageConstants.TARGET_LIST[0].val });
    return h('div', null, this.renderAccountRegionClusterSelector(), this.renderTargetSelect());
  }
}

function syncTargetClusterFields(stage) {
  if (stage.targetCluster) {
    const clusterName = NameUtils.parseServerGroupName(stage.targetCluster);
    stage.stack = clusterName.stack;
    stage.freeFormDetails = clusterName.freeFormDetails;
  } else {
    stage.stack = '';
    stage.freeFormDetails = '';
  }
}

export class GceCloneServerGroupStageConfig extends GceBaseStageConfig {
  render() {
    const { application, stage } = this.props;
    initializeGceStage(stage, application, { target: StageConstants.TARGET_LIST[0].val });
    setDefault(stage, 'application', application?.name);
    setDefault(stage, 'useSourceCapacity', true);
    syncTargetClusterFields(stage);

    const updateCapacity = (capacity) => updateStage(this.props, { capacity, useSourceCapacity: false });
    const useSourceCapacity = () => {
      const nextStage = { ...stage, useSourceCapacity: true };
      delete nextStage.capacity;
      replaceStage(this.props, nextStage);
    };

    return h(
      'div',
      null,
      this.renderAccountRegionClusterSelector({ clusterField: 'targetCluster', singleRegion: true }),
      this.renderTargetSelect(),
      h(
        'div',
        null,
        h(
          'div',
          { className: 'form-group' },
          h('div', { className: 'col-md-3 sm-label-right' }, 'Capacity'),
          h(
            'div',
            { className: 'col-md-9 radio' },
            h(
              'label',
              null,
              h('input', { checked: stage.useSourceCapacity === true, type: 'radio', onChange: useSourceCapacity }),
              ' Copy the capacity from the current server group',
            ),
          ),
          h(
            'div',
            { className: 'col-md-9 col-md-offset-3 radio' },
            h(
              'label',
              null,
              h('input', {
                checked: stage.useSourceCapacity === false,
                type: 'radio',
                onChange: () => updateStage(this.props, { capacity: stage.capacity || {}, useSourceCapacity: false }),
              }),
              ' Let me specify the capacity',
            ),
          ),
        ),
        h(CapacityFields, { capacity: stage.capacity, disabled: stage.useSourceCapacity, onChange: updateCapacity }),
      ),
      h(
        StageConfigField,
        { label: 'Traffic' },
        h(
          'div',
          { className: 'checkbox' },
          h(
            'label',
            null,
            h('input', {
              checked: !stage.disableTraffic,
              type: 'checkbox',
              onChange: (e) => updateStage(this.props, { disableTraffic: !e.target.checked }),
            }),
            ' Send client requests to new instances',
          ),
        ),
      ),
      h(GcePlatformHealthOverride, {
        application,
        stage,
        updateStage: (nextStage) => replaceStage(this.props, nextStage),
      }),
      this.renderDeploymentStrategySelector(),
    );
  }
}

export class GceFindImageStageConfig extends GceBaseStageConfig {
  render() {
    const { application, stage } = this.props;
    initializeGceStage(stage, application, { selectionStrategy: gceSelectionStrategies[0].val, onlyEnabled: true });
    return h(
      'div',
      null,
      this.renderAccountRegionClusterSelector(),
      h(
        StageConfigField,
        { label: 'Server Group Selection' },
        h(SelectInput, {
          value: stage.selectionStrategy,
          options: gceSelectionStrategies,
          onChange: (selectionStrategy) => updateStage(this.props, { selectionStrategy }),
        }),
      ),
      h(
        StageConfigField,
        { label: 'Server Group Filters' },
        h(
          'div',
          { className: 'checkbox' },
          h(
            'label',
            null,
            h('input', {
              checked: stage.onlyEnabled,
              type: 'checkbox',
              onChange: (e) => updateStage(this.props, { onlyEnabled: e.target.checked }),
            }),
            ' Only consider enabled Server Groups',
          ),
        ),
      ),
    );
  }
}

function normalizeResizeStage(stage) {
  if (stage.action === 'scale_exact') {
    stage.resizeType = 'exact';
    delete stage.scalePct;
    delete stage.scaleNum;
    stage.capacity = stage.capacity || {};
  } else {
    stage.capacity = {};
    if (stage.resizeType === 'pct') {
      delete stage.scaleNum;
    } else {
      stage.resizeType = 'incr';
      delete stage.scalePct;
      stage.scaleNum = stage.scaleNum || 0;
    }
  }
}

export class GceResizeServerGroupStageConfig extends GceBaseStageConfig {
  render() {
    const { application, stage } = this.props;
    initializeGceStage(stage, application, {
      target: StageConstants.TARGET_LIST[0].val,
      action: scaleActions[0].val,
      resizeType: resizeTypes[0].val,
    });
    stage.capacity = stage.capacity || {};
    if (!stage.action && stage.resizeType === 'exact') {
      stage.action = 'scale_exact';
    }

    const updateResize = (changes) => {
      const nextStage = { ...stage, ...changes };
      normalizeResizeStage(nextStage);
      replaceStage(this.props, nextStage);
    };

    return h(
      'div',
      null,
      this.renderAccountRegionClusterSelector(),
      this.renderTargetSelect(),
      h(
        StageConfigField,
        { label: 'Action' },
        h(SelectInput, { value: stage.action, options: scaleActions, onChange: (action) => updateResize({ action }) }),
      ),
      stage.action !== 'scale_exact' &&
        h(
          React.Fragment,
          null,
          h(
            StageConfigField,
            { label: stage.action === 'scale_to_cluster' ? 'Additional Capacity' : 'Type' },
            h(SelectInput, {
              value: stage.resizeType,
              options: resizeTypes,
              onChange: (resizeType) => updateResize({ resizeType }),
            }),
          ),
          stage.resizeType === 'pct' &&
            h(
              StageConfigField,
              { label: 'Resize Percentage' },
              h(NumberInput, { value: stage.scalePct, onChange: (scalePct) => updateStage(this.props, { scalePct }) }),
            ),
          stage.resizeType === 'incr' &&
            h(
              StageConfigField,
              { label: 'Resize-by Amount' },
              h(NumberInput, { value: stage.scaleNum, onChange: (scaleNum) => updateStage(this.props, { scaleNum }) }),
            ),
        ),
      stage.action === 'scale_exact' &&
        h(CapacityFields, {
          capacity: stage.capacity,
          onChange: (capacity) => updateStage(this.props, { capacity }),
        }),
      h(GcePlatformHealthOverride, {
        application,
        stage,
        updateStage: (nextStage) => replaceStage(this.props, nextStage),
      }),
    );
  }
}

export class GceClusterCapacityStageConfig extends GceBaseStageConfig {
  render() {
    const {
      application,
      stage,
      activeToggleField,
      activeToggleLabel,
      defaultValue = 1,
      fieldName,
      label,
      preferenceField = 'preferLargerOverNewer',
      preferenceLabel = 'Keep',
      showHealthOverride = false,
    } = this.props;
    initializeGceStage(stage, application, { [fieldName]: defaultValue });
    setDefault(stage, preferenceField, 'false');
    stage[preferenceField] = stage[preferenceField].toString();

    if (activeToggleField) {
      setDefault(stage, activeToggleField, false);
    }

    return h(
      'div',
      null,
      this.renderAccountRegionClusterSelector(),
      h(
        StageConfigField,
        { label },
        h(
          'div',
          { className: 'form-inline' },
          `${preferenceLabel} `,
          h(NumberInput, {
            style: { display: 'inline-block', width: '50px' },
            value: stage[fieldName],
            onChange: (value) => updateStage(this.props, { [fieldName]: value }),
          }),
          ' ',
          h(InlineSelectInput, {
            style: { display: 'inline-block', width: '100px' },
            value: stage[preferenceField],
            options: [
              { label: 'largest', val: 'true' },
              { label: 'newest', val: 'false' },
            ],
            onChange: (value) => updateStage(this.props, { [preferenceField]: value }),
          }),
          ` ${pluralize('server group', stage[fieldName])}.`,
        ),
      ),
      activeToggleField &&
        h(
          'div',
          { className: 'form-group' },
          h(
            'div',
            { className: 'col-md-offset-3 col-md-6 checkbox' },
            h(
              'label',
              null,
              h('input', {
                checked: !!stage[activeToggleField],
                type: 'checkbox',
                onChange: (e) => updateStage(this.props, { [activeToggleField]: e.target.checked }),
              }),
              ` ${activeToggleLabel}`,
            ),
          ),
        ),
      showHealthOverride &&
        h(GcePlatformHealthOverride, {
          application,
          stage,
          updateStage: (nextStage) => replaceStage(this.props, nextStage),
        }),
    );
  }
}

export function GceDisableClusterStageConfig(props) {
  return h(GceClusterCapacityStageConfig, {
    ...props,
    fieldName: 'remainingEnabledServerGroups',
    label: 'Disable Options',
    preferenceLabel: 'Keep the',
    showHealthOverride: true,
  });
}

export function GceScaleDownClusterStageConfig(props) {
  return h(GceClusterCapacityStageConfig, {
    ...props,
    activeToggleField: 'allowScaleDownActive',
    activeToggleLabel: 'Allow scale down of active server groups',
    fieldName: 'remainingFullSizeServerGroups',
    label: 'Scale Down Options',
    preferenceLabel: 'Keep the',
  });
}

export function GceShrinkClusterStageConfig(props) {
  return h(GceClusterCapacityStageConfig, {
    ...props,
    activeToggleField: 'allowDeleteActive',
    activeToggleLabel: 'Allow deletion of active server groups',
    fieldName: 'shrinkToSize',
    label: 'Shrink Options',
    preferenceField: 'retainLargerOverNewer',
    preferenceLabel: 'Shrink to',
    showHealthOverride: true,
  });
}
