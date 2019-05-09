import * as React from 'react';
import * as classNames from 'classnames';
import { connect } from 'react-redux';
import { Modal } from 'react-bootstrap';
import { get, isNull, values } from 'lodash';

import { noop } from '@spinnaker/core';
import * as Creators from 'kayenta/actions/creators';
import { ICanaryState } from 'kayenta/reducers';
import { ICanaryMetricConfig, ICanaryMetricEffectSizeConfig } from 'kayenta/domain';
import MetricConfigurerDelegator from './metricConfigurerDelegator';
import Styleguide from 'kayenta/layout/styleguide';
import FormRow from 'kayenta/layout/formRow';
import RadioChoice from 'kayenta/layout/radioChoice';
import { DisableableInput, DisableableSelect, DISABLE_EDIT_CONFIG } from 'kayenta/layout/disableable';
import { editingMetricValidationErrorsSelector } from 'kayenta/selectors';
import { isTemplateValidSelector, useInlineTemplateEditorSelector } from 'kayenta/selectors/filterTemplatesSelectors';
import { CanarySettings } from 'kayenta/canary.settings';
import { ICanaryMetricValidationErrors } from './editMetricValidation';
import FilterTemplateSelector from './filterTemplateSelector';
import metricStoreConfigService from 'kayenta/metricStore/metricStoreConfig.service';
import InlineTemplateEditor from './inlineTemplateEditor';

import './editMetricModal.less';

interface IEditMetricModalDispatchProps {
  rename: (event: any) => void;
  changeGroup: (event: any) => void;
  updateDirection: (event: any) => void;
  updateNanStrategy: (event: any) => void;
  updateCriticality: (event: any) => void;
  confirm: () => void;
  cancel: () => void;
  updateScopeName: (event: any) => void;
}

interface IEditMetricModalStateProps {
  metric: ICanaryMetricConfig;
  groups: string[];
  isTemplateValid: boolean;
  useInlineTemplateEditor: boolean;
  disableEdit: boolean;
  validationErrors: ICanaryMetricValidationErrors;
}

function EffectSizeSummary({ effectSizes }: { effectSizes: ICanaryMetricEffectSizeConfig }) {
  if (!effectSizes || Object.keys(effectSizes).length === 0) {
    return null;
  }

  const { allowedIncrease, criticalIncrease, allowedDecrease, criticalDecrease } = effectSizes;

  return (
    <FormRow label="Effect Sizes">
      <div className="vertical">
        {allowedIncrease && (
          <span>
            Allowed Increase: <b>{allowedIncrease}</b>
          </span>
        )}
        {criticalIncrease && (
          <span>
            Critical Increase: <b>{criticalIncrease}</b>
          </span>
        )}
        {allowedDecrease && (
          <span>
            Allowed Decrease: <b>{allowedDecrease}</b>
          </span>
        )}
        {criticalDecrease && (
          <span>
            Critical Decrease: <b>{criticalDecrease}</b>
          </span>
        )}
        <span className="body-small color-text-caption" style={{ marginTop: '5px' }}>
          Effect sizes are not currently configurable via the UI.
        </span>
      </div>
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
  isTemplateValid,
  updateDirection,
  updateNanStrategy,
  updateCriticality,
  updateScopeName,
  useInlineTemplateEditor,
  disableEdit,
  validationErrors,
}: IEditMetricModalDispatchProps & IEditMetricModalStateProps) {
  if (!metric) {
    return null;
  }

  const direction = get(metric, ['analysisConfigurations', 'canary', 'direction'], 'either');
  const nanStrategy = get(metric, ['analysisConfigurations', 'canary', 'nanStrategy'], 'default');
  const critical = get(metric, ['analysisConfigurations', 'canary', 'critical'], false);
  const effectSize = get<ICanaryMetricConfig, ICanaryMetricEffectSizeConfig>(metric, [
    'analysisConfigurations',
    'canary',
    'effectSize',
  ]);
  const isConfirmDisabled = !isTemplateValid || disableEdit || values(validationErrors).some(e => !isNull(e));

  const metricGroup = metric.groups.length ? metric.groups[0] : groups[0];
  const templatesEnabled =
    metricStoreConfigService.getDelegate(metric.query.type) &&
    metricStoreConfigService.getDelegate(metric.query.type).useTemplates &&
    CanarySettings.templatesEnabled;
  return (
    <Modal bsSize="large" show={true} onHide={noop} className={classNames('kayenta-edit-metric-modal')}>
      <Styleguide>
        <Modal.Header>
          <Modal.Title>{disableEdit ? 'Metric Details' : 'Configure Metric'}</Modal.Title>
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
                {groups.map(g => (
                  <option key={g} value={g}>
                    {g}
                  </option>
                ))}
              </DisableableSelect>
            )}
          </FormRow>
          <FormRow label="Name" error={get(validationErrors, 'name.message', null)}>
            <DisableableInput
              type="text"
              value={metric.name}
              data-id={metric.id}
              onChange={rename}
              disabledStateKeys={[DISABLE_EDIT_CONFIG]}
            />
          </FormRow>
          <FormRow label="Fail on">
            <RadioChoice
              value="increase"
              label="Increase"
              name="direction"
              current={direction}
              action={updateDirection}
            />
            <RadioChoice
              value="decrease"
              label="Decrease"
              name="direction"
              current={direction}
              action={updateDirection}
            />
            <RadioChoice value="either" label="Either" name="direction" current={direction} action={updateDirection} />
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
          <FormRow label="NaN Strategy" helpId="canary.config.nanStrategy">
            <RadioChoice
              value="default"
              label="Default (remove)"
              name="nanStrategy"
              current={nanStrategy}
              action={updateNanStrategy}
            />
            <RadioChoice
              value="replace"
              label="Replace with zero"
              name="nanStrategy"
              current={nanStrategy}
              action={updateNanStrategy}
            />
            <RadioChoice
              value="remove"
              label="Remove"
              name="nanStrategy"
              current={nanStrategy}
              action={updateNanStrategy}
            />
          </FormRow>
          <FormRow label="Scope Name" error={get(validationErrors, 'scopeName.message', null)}>
            <DisableableInput
              type="text"
              value={metric.scopeName}
              onChange={updateScopeName}
              disabledStateKeys={[DISABLE_EDIT_CONFIG]}
            />
          </FormRow>
          <EffectSizeSummary effectSizes={effectSize} />
          <MetricConfigurerDelegator />
          {templatesEnabled && !useInlineTemplateEditor && <FilterTemplateSelector />}
          {templatesEnabled && useInlineTemplateEditor && <InlineTemplateEditor />}
        </Modal.Body>
        <Modal.Footer>
          <ul className="list-inline pull-right">
            <li>
              <button className="passive" onClick={cancel}>
                Cancel
              </button>
            </li>
            <li>
              <button className="primary" disabled={isConfirmDisabled} onClick={confirm}>
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
      dispatch(Creators.editTemplateCancel());
    },
    confirm: () => {
      dispatch(Creators.editMetricConfirm());
      dispatch(Creators.editTemplateCancel());
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
    updateScopeName: (event: any) => dispatch(Creators.updateMetricScopeName({ scopeName: event.target.value })),
  };
}

function mapStateToProps(state: ICanaryState): IEditMetricModalStateProps {
  return {
    metric: state.selectedConfig.editingMetric,
    groups: state.selectedConfig.group.list.sort(),
    isTemplateValid: isTemplateValidSelector(state),
    useInlineTemplateEditor: useInlineTemplateEditorSelector(state),
    disableEdit: state.app.disableConfigEdit,
    validationErrors: editingMetricValidationErrorsSelector(state),
  };
}

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(EditMetricModal);
