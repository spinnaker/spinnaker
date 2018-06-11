import * as React from 'react';
import { connect } from 'react-redux';
import { Modal } from 'react-bootstrap';
import { get } from 'lodash';
import { Option } from 'react-select';
import { noop, HelpField } from '@spinnaker/core';
import * as Creators from 'kayenta/actions/creators';
import { ICanaryState } from 'kayenta/reducers';
import { ICanaryMetricConfig } from 'kayenta/domain';
import MetricConfigurerDelegator from './metricConfigurerDelegator';
import metricStoreConfigService from 'kayenta/metricStore/metricStoreConfig.service';
import Styleguide from 'kayenta/layout/styleguide';
import FormRow from 'kayenta/layout/formRow';
import { DisableableInput, DisableableSelect, DisableableReactSelect, DISABLE_EDIT_CONFIG } from 'kayenta/layout/disableable';
import { configTemplatesSelector } from 'kayenta/selectors';
import { CanarySettings } from 'kayenta/canary.settings';

import './editMetricModal.less';

interface IEditMetricModalDispatchProps {
  rename: (event: any) => void;
  changeGroup: (event: any) => void;
  updateDirection: (event: any) => void;
  updateNanStrategy: (event: any) => void;
  updateCriticality: (event: any) => void;
  confirm: () => void;
  cancel: () => void;
  selectTemplate: (template: Option) => void;
  updateScopeName: (event: any) => void;
}

interface IEditMetricModalStateProps {
  metric: ICanaryMetricConfig;
  templates: Option[];
  filterTemplate: string;
  groups: string[];
}

function RadioChoice({ value, label, name, current, action }: { value: string, label: string, name: string, current: string, action: (event: any) => void }) {
  return (
    <label style={{ fontWeight: 'normal', marginRight: '1em' }}>
      <DisableableInput
        type="radio"
        name={name}
        value={value}
        onChange={action}
        checked={value === current}
        disabledStateKeys={[DISABLE_EDIT_CONFIG]}
      />
        {' '}{label}
    </label>
  );
}

interface IFilterTemplateSelectorProps {
  metricStore: string;
  templates: Option[];
  template: string;
  select: (template: Option) => void;
}

function FilterTemplateSelector({ metricStore, template, templates, select }: IFilterTemplateSelectorProps) {
  const config = metricStoreConfigService.getDelegate(metricStore);
  if (!config || !config.useTemplates) {
    return null;
  }

  if (!CanarySettings.templatesEnabled) {
    return null;
  }

  return (
    <FormRow label="Filter Template">
      <DisableableReactSelect
        value={template}
        options={templates}
        onChange={select}
        disabledStateKeys={[DISABLE_EDIT_CONFIG]}
      />
    </FormRow>
  );
}

/*
 * Modal to edit metric details.
 */
function EditMetricModal({
  metric,
  rename,
  changeGroup,
  groups,
  confirm,
  cancel,
  updateDirection,
  updateNanStrategy,
  updateCriticality,
  templates,
  selectTemplate,
  filterTemplate,
  updateScopeName
}: IEditMetricModalDispatchProps & IEditMetricModalStateProps) {
  if (!metric) {
    return null;
  }

  const direction = get(metric, ['analysisConfigurations', 'canary', 'direction'], 'either');
  const nanStrategy = get(metric, ['analysisConfigurations', 'canary', 'nanStrategy'], 'default');
  const critical = get(metric, ['analysisConfigurations', 'canary', 'critical'], false);

  const metricGroup = metric.groups.length ? metric.groups[0] : groups[0];
  return (
    <Modal show={true} onHide={noop} className="kayenta-edit-metric-modal">
      <Styleguide>
        <Modal.Header>
          <Modal.Title>Configure Metric</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <FormRow label="Group">
            {metric.groups.length > 1 && (
              <DisableableInput
                type="text"
                value={metric.groups}
                data-id={metric.id}
                onChange={changeGroup}
                disabledStateKeys={[DISABLE_EDIT_CONFIG]}
              />
            )}
            {metric.groups.length < 2 && (
              <DisableableSelect
                value={metricGroup}
                onChange={changeGroup}
                className="form-control input-sm"
                disabledStateKeys={[DISABLE_EDIT_CONFIG]}
              >
                {
                  groups.map(g => (
                    <option key={g} value={g}>{g}</option>
                  ))
                }
              </DisableableSelect>
            )}
          </FormRow>
          <FormRow label="Name">
            <DisableableInput
              type="text"
              value={metric.name}
              data-id={metric.id}
              onChange={rename}
              disabledStateKeys={[DISABLE_EDIT_CONFIG]}
            />
          </FormRow>
          <FormRow label="Fail on">
            <RadioChoice value="increase" label="Increase" name="direction" current={direction} action={updateDirection}/>
            <RadioChoice value="decrease" label="Decrease" name="direction" current={direction} action={updateDirection}/>
            <RadioChoice value="either"   label="Either"   name="direction" current={direction} action={updateDirection}/>
          </FormRow>
          <FormRow label="Criticality" checkbox={true}>
            <label>
              <DisableableInput
                type="checkbox"
                checked={critical}
                onChange={updateCriticality}
                disabledStateKeys={[DISABLE_EDIT_CONFIG]}
              />
              Fail the canary if this metric fails
            </label>
          </FormRow>
          <FormRow label={<>NaN Strategy <HelpField id="canary.config.nanStrategy"/></>}>
            <RadioChoice value="default" label="Default (remove)"  name="nanStrategy" current={nanStrategy} action={updateNanStrategy}/>
            <RadioChoice value="replace" label="Replace with zero" name="nanStrategy" current={nanStrategy} action={updateNanStrategy}/>
            <RadioChoice value="remove"  label="Remove"            name="nanStrategy" current={nanStrategy} action={updateNanStrategy}/>
          </FormRow>
          <FilterTemplateSelector
            metricStore={metric.query.type}
            templates={templates}
            template={filterTemplate}
            select={selectTemplate}
          />
          <FormRow label="Scope Name">
            <DisableableInput
              type="text"
              value={metric.scopeName}
              onChange={updateScopeName}
              disabledStateKeys={[DISABLE_EDIT_CONFIG]}
            />
          </FormRow>
          <MetricConfigurerDelegator/>
        </Modal.Body>
        <Modal.Footer>
          <ul className="list-inline pull-right">
            <li><button className="passive" onClick={cancel}>Cancel</button></li>
            <li>
              <button
                className="primary"
                disabled={!metric.name || !metric.scopeName}
                onClick={confirm}
              >
                OK
              </button>
            </li>
          </ul>
        </Modal.Footer>
      </Styleguide>
    </Modal>
  );
}

function mapDispatchToProps(dispatch: any): IEditMetricModalDispatchProps {
  return {
    rename: (event: any) => {
      dispatch(Creators.renameMetric({ id: event.target.dataset.id, name: event.target.value }));
    },
    changeGroup: (event: any) => {
      dispatch(Creators.updateMetricGroup({ id: event.target.dataset.id, group: event.target.value }));
    },
    cancel: () => {
      dispatch(Creators.editMetricCancel());
    },
    confirm: () => {
      dispatch(Creators.editMetricConfirm());
    },
    updateDirection: ({ target }: React.ChangeEvent<HTMLInputElement>) => {
      dispatch(Creators.updateMetricDirection({ id: target.dataset.id, direction: target.value }));
    },
    updateNanStrategy: ({ target }: React.ChangeEvent<HTMLInputElement>) => {
      dispatch(Creators.updateMetricNanStrategy({ id: target.dataset.id, strategy: target.value }));
    },
    updateCriticality: ({ target }: React.ChangeEvent<HTMLInputElement>) => {
      dispatch(Creators.updateMetricCriticality({ id: target.dataset.id, critical: Boolean(target.checked) }));
    },
    selectTemplate: (template: Option) =>
      dispatch(Creators.selectTemplate({ name: template ? template.value as string : null })),
    updateScopeName: (event: any) =>
      dispatch(Creators.updateMetricScopeName({ scopeName: event.target.value }))
  };
}

function mapStateToProps(state: ICanaryState): IEditMetricModalStateProps {
  return {
    metric: state.selectedConfig.editingMetric,
    templates: Object.keys(configTemplatesSelector(state) || {}).map(t => ({
      label: t, value: t,
    })),
    filterTemplate: get(state, 'selectedConfig.editingMetric.query.customFilterTemplate'),
    groups: state.selectedConfig.group.list.sort(),
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(EditMetricModal);
