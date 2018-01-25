import * as React from 'react';
import { connect } from 'react-redux';
import { Modal } from 'react-bootstrap';
import { get } from 'lodash';
import * as Select from 'react-select';
import { noop } from '@spinnaker/core';
import * as Creators from '../actions/creators';
import {ICanaryState} from '../reducers/index';
import {ICanaryMetricConfig} from 'kayenta/domain';
import MetricConfigurerDelegator from './metricConfigurerDelegator';
import metricStoreConfigService from 'kayenta/metricStore/metricStoreConfig.service';
import Styleguide from '../layout/styleguide';
import FormRow from '../layout/formRow';
import KayentaInput from '../layout/kayentaInput';
import { configTemplatesSelector } from 'kayenta/selectors';

import './editMetricModal.less';

interface IEditMetricModalDispatchProps {
  rename: (event: any) => void;
  updateDirection: (event: any) => void;
  confirm: () => void;
  cancel: () => void;
  selectTemplate: (template: Select.Option) => void;
  updateScopeName: (event: any) => void;
}

interface IEditMetricModalStateProps {
  metric: ICanaryMetricConfig;
  templates: Select.Option[];
  filterTemplate: string;
}

function DirectionChoice({ value, label, current, action }: { value: string, label: string, current: string, action: (event: any) => void }) {
  return (
    <label style={{fontWeight: 'normal', marginRight: '1em'}}>
      <input name="direction" type="radio" value={value} onClick={action} checked={value === current}/> {label}
    </label>
  );
}

interface IFilterTemplateSelectorProps {
  metricStore: string;
  templates: Select.Option[];
  template: string;
  select: (template: Select.Option) => void;
}

function FilterTemplateSelector({ metricStore, template, templates, select }: IFilterTemplateSelectorProps) {
  const config = metricStoreConfigService.getDelegate(metricStore);
  if (!config || !config.useTemplates) {
    return null;
  }

  return (
    <FormRow label="Filter Template">
      <Select
        value={template}
        options={templates}
        onChange={select}
      />
    </FormRow>
  );
}

/*
 * Modal to edit metric details.
 */
function EditMetricModal({ metric, rename, confirm, cancel, updateDirection, templates, selectTemplate, filterTemplate, updateScopeName }: IEditMetricModalDispatchProps & IEditMetricModalStateProps) {
  if (!metric) {
    return null;
  }

  const direction = get(metric, ['analysisConfigurations', 'canary', 'direction'], 'either');
  return (
    <Modal show={true} onHide={noop} className="kayenta-edit-metric-modal">
      <Styleguide>
        <Modal.Header>
          <Modal.Title>Configure Metric</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <FormRow label="Name">
            <KayentaInput
              type="text"
              value={metric.name}
              data-id={metric.id}
              onChange={rename}
            />
          </FormRow>
          <FormRow label="Fail on">
            <DirectionChoice value="increase" label="increase" current={direction} action={updateDirection}/>
            <DirectionChoice value="decrease" label="decrease" current={direction} action={updateDirection}/>
            <DirectionChoice value="either"   label="either"   current={direction} action={updateDirection}/>
          </FormRow>
          <FilterTemplateSelector
            metricStore={metric.query.type}
            templates={templates}
            template={filterTemplate}
            select={selectTemplate}
          />
          <FormRow label="Scope Name">
            <KayentaInput
              type="text"
              value={metric.scopeName}
              onChange={updateScopeName}
            />
          </FormRow>
          <MetricConfigurerDelegator/>
        </Modal.Body>
        <Modal.Footer>
          <ul className="list-inline pull-right">
            <li><button className="passive" onClick={cancel}>Cancel</button></li>
            <li><button className="primary" disabled={!metric.name || !metric.scopeName} onClick={confirm}>OK</button></li>
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
    cancel: () => {
      dispatch(Creators.editMetricCancel());
    },
    confirm: () => {
      dispatch(Creators.editMetricConfirm());
    },
    updateDirection: (event: any) => {
      dispatch(Creators.updateMetricDirection({ id: event.target.dataset.id, direction: event.target.value }))
    },
    selectTemplate: (template: Select.Option) =>
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
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(EditMetricModal);
