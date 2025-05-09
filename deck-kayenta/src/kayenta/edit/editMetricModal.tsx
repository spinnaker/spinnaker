import classNames from 'classnames';
import * as Creators from 'kayenta/actions/creators';
import { CanarySettings } from 'kayenta/canary.settings';
import { ICanaryMetricConfig } from 'kayenta/domain';
import { DISABLE_EDIT_CONFIG, DisableableInput, DisableableSelect } from 'kayenta/layout/disableable';
import FormRow from 'kayenta/layout/formRow';
import RadioChoice from 'kayenta/layout/radioChoice';
import Styleguide from 'kayenta/layout/styleguide';
import metricStoreConfigService from 'kayenta/metricStore/metricStoreConfig.service';
import { ICanaryState } from 'kayenta/reducers';
import { editingMetricValidationErrorsSelector } from 'kayenta/selectors';
import { isTemplateValidSelector, useInlineTemplateEditorSelector } from 'kayenta/selectors/filterTemplatesSelectors';
import { isNull, values } from 'lodash';
import * as React from 'react';
import { Modal } from 'react-bootstrap';
import { connect } from 'react-redux';

import { noop } from '@spinnaker/core';

import EditMetricEffectSizes from './editMetricEffectSizes';
import { ICanaryMetricValidationErrors } from './editMetricValidation';
import FilterTemplateSelector from './filterTemplateSelector';
import InlineTemplateEditor from './inlineTemplateEditor';
import MetricConfigurerDelegator from './metricConfigurerDelegator';

import './editMetricModal.less';

interface IEditMetricModalDispatchProps {
  rename: (event: any) => void;
  changeGroup: (event: any) => void;
  updateDirection: (event: any) => void;
  updateNanStrategy: (event: any) => void;
  updateCriticality: (event: any) => void;
  updateDataRequired: (event: any) => void;
  confirm: () => void;
  cancel: () => void;
}

interface IEditMetricModalStateProps {
  metric: ICanaryMetricConfig;
  groups: string[];
  isTemplateValid: boolean;
  useInlineTemplateEditor: boolean;
  disableEdit: boolean;
  validationErrors: ICanaryMetricValidationErrors;
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
  updateDataRequired,
  useInlineTemplateEditor,
  disableEdit,
  validationErrors,
}: IEditMetricModalDispatchProps & IEditMetricModalStateProps) {
  if (!metric) {
    return null;
  }

  const direction = metric.analysisConfigurations?.canary?.direction ?? 'either';
  const nanStrategy = metric.analysisConfigurations?.canary?.nanStrategy ?? 'default';
  const critical = metric.analysisConfigurations?.canary?.critical ?? false;
  const dataRequired = metric.analysisConfigurations?.canary?.mustHaveData ?? false;
  const isConfirmDisabled =
    !isTemplateValid ||
    disableEdit ||
    CanarySettings.disableConfigEdit ||
    values(validationErrors).some((e) => !isNull(e));

  const metricGroup = metric.groups.length ? metric.groups[0] : groups[0];
  const templatesEnabled =
    metricStoreConfigService.getDelegate(metric.query.type) &&
    metricStoreConfigService.getDelegate(metric.query.type).useTemplates &&
    CanarySettings.templatesEnabled;
  return (
    <Modal bsSize="large" show={true} onHide={noop} className={classNames('kayenta-edit-metric-modal')}>
      <Styleguide>
        <Modal.Header>
          <Modal.Title>
            {disableEdit || CanarySettings.disableConfigEdit ? 'Metric Details' : 'Configure Metric'}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <FormRow label="Group" inputOnly={true}>
            {metric.groups.length > 1 && (
              <DisableableInput
                type="text"
                value={metric.groups}
                data-id={metric.id}
                onChange={changeGroup}
                disabled={CanarySettings.disableConfigEdit}
                disabledStateKeys={[DISABLE_EDIT_CONFIG]}
              />
            )}
            {metric.groups.length < 2 && (
              <DisableableSelect
                value={metricGroup}
                onChange={changeGroup}
                className="form-control input-sm"
                disabled={CanarySettings.disableConfigEdit}
                disabledStateKeys={[DISABLE_EDIT_CONFIG]}
              >
                {groups.map((g) => (
                  <option key={g} value={g}>
                    {g}
                  </option>
                ))}
              </DisableableSelect>
            )}
          </FormRow>
          <FormRow label="Name" error={validationErrors?.name?.message ?? null} inputOnly={true}>
            <DisableableInput
              type="text"
              value={metric.name}
              data-id={metric.id}
              onChange={rename}
              disabled={CanarySettings.disableConfigEdit}
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
                name="criticality"
                checked={critical}
                onChange={updateCriticality}
                disabled={CanarySettings.disableConfigEdit}
                disabledStateKeys={[DISABLE_EDIT_CONFIG]}
              />
              Fail the canary if this metric fails
            </label>
          </FormRow>
          <FormRow label="Data Required" checkbox={true}>
            <label>
              <DisableableInput
                type="checkbox"
                name="dataRequired"
                checked={dataRequired}
                onChange={updateDataRequired}
                disabled={CanarySettings.disableConfigEdit}
                disabledStateKeys={[DISABLE_EDIT_CONFIG]}
              />
              Fail the metric if data is missing
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
          <EditMetricEffectSizes />
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
    updateDataRequired: ({ target }: React.ChangeEvent<HTMLInputElement>) => {
      dispatch(Creators.updateMetricDataRequired({ id: target.dataset.id, mustHaveData: Boolean(target.checked) }));
    },
  };
}

function mapStateToProps(state: ICanaryState): IEditMetricModalStateProps {
  return {
    metric: state.selectedConfig.editingMetric,
    groups: state.selectedConfig.group.list.sort(),
    isTemplateValid: isTemplateValidSelector(state),
    // eslint-disable-next-line react-hooks/rules-of-hooks
    useInlineTemplateEditor: useInlineTemplateEditorSelector(state),
    disableEdit: state.app.disableConfigEdit || CanarySettings.disableConfigEdit,
    validationErrors: editingMetricValidationErrorsSelector(state),
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(EditMetricModal);
