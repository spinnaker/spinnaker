import * as Creators from 'kayenta/actions/creators';
import { ICanaryMetricEffectSizeConfig } from 'kayenta/domain';
import FormRow from 'kayenta/layout/formRow';
import RadioChoice from 'kayenta/layout/radioChoice';
import { ICanaryState } from 'kayenta/reducers';
import * as React from 'react';
import { connect } from 'react-redux';

import { HelpField, robotToHuman } from '@spinnaker/core';

import { DISABLE_EDIT_CONFIG, DisableableInput } from '../layout/disableable';

interface IEditMetricEffectSizesProps {
  metricId: string;
  direction: string;
  disabled: boolean;
  effectSizes: ICanaryMetricEffectSizeConfig;
}

interface IEditMetricEffectSizesDispatchProps {
  updateEffectSize: (id: string, value: ICanaryMetricEffectSizeConfig) => void;
}

export function EditMetricEffectSizes({
  effectSizes = {},
  updateEffectSize,
  direction,
  metricId,
  disabled,
}: IEditMetricEffectSizesProps & IEditMetricEffectSizesDispatchProps) {
  const [showEffectSizes, setShowEffectSizes] = React.useState<boolean>(!!Object.keys(effectSizes).length);
  // only allow editing of measurement type if it was set to "cles" programmatically
  const [allowEditMeasurementType] = React.useState<boolean>(effectSizes.measure === 'cles');

  const description = (
    <p>
      Effect sizes should be used when your canary data produces a lot of metrics that flag as "high", but where you
      only want the test to fail if they are sufficiently larger or smaller than the control data.
    </p>
  );

  if (!showEffectSizes && disabled) {
    return null;
  }

  if (!showEffectSizes) {
    return (
      <FormRow label="Effect Sizes">
        {description}
        <button className="passive" onClick={() => setShowEffectSizes(true)}>
          Configure effect sizes
        </button>
      </FormRow>
    );
  }

  const isCles = effectSizes.measure === 'cles';
  const defaultSize = isCles ? 0.5 : 1;

  const {
    allowedIncrease = defaultSize,
    criticalIncrease = defaultSize,
    allowedDecrease = defaultSize,
    criticalDecrease = defaultSize,
    measure = 'meanRatio',
  } = effectSizes;

  const updateSize = (field: keyof ICanaryMetricEffectSizeConfig, percent: string) => {
    const value = isCles ? Number(percent) : fromPercent(field, percent);
    const newEffectSizes = { ...effectSizes, [field]: value };
    if (value === defaultSize) {
      delete newEffectSizes[field];
    }
    updateEffectSize(metricId, newEffectSizes);
  };

  const updateMeasure = (event: React.ChangeEvent<HTMLInputElement>) => {
    updateEffectSize(metricId, { ...effectSizes, measure: event.target.value });
  };

  const fromPercent = (field: keyof ICanaryMetricEffectSizeConfig, percent: string) => {
    const size = Number(percent) / 100;
    return field.endsWith('Increase') ? 1 + size : 1 - size;
  };

  const toPercent = (value: number, field: keyof ICanaryMetricEffectSizeConfig) => {
    const normalized = field.endsWith('Increase') ? value - 1 : 1 - value;
    return Math.round(normalized * 100);
  };

  const clesLabel = (
    <>
      CLES <HelpField id="canary.config.effectSize.cles" />
    </>
  );

  const noEffectLabel = (effect: number) => {
    if (effect === defaultSize) {
      return <HelpField expand={true} content="(will not affect metric evaluation)" />;
    }
    return null;
  };

  const buildInput = (field: keyof ICanaryMetricEffectSizeConfig, value: number) => (
    <div className="form-group flex-1">
      <label>{robotToHuman(field)}</label>
      <div className="row">
        <div className="col-md-4">
          <div className="input-group effect-size-wrapper">
            <DisableableInput
              className="form-control input-sm"
              type="number"
              min={0}
              step={isCles ? 0.01 : 1}
              onChange={(e) => updateSize(field, e.target.value)}
              value={isCles ? value : toPercent(value, field)}
              disabledStateKeys={[DISABLE_EDIT_CONFIG]}
            />
            {!isCles && <div className="input-group-addon">%</div>}
          </div>
        </div>
      </div>
      {noEffectLabel(value)}
    </div>
  );

  return (
    <FormRow label="Effect Sizes">
      {description}
      <div className="vertical" style={{ marginTop: '5px' }}>
        {allowEditMeasurementType && (
          <div className="form-group vertical">
            <label>Measurement Type</label>
            <div>
              <RadioChoice
                value="meanRatio"
                label="Mean (default)"
                name="measure"
                current={measure}
                action={updateMeasure}
              />
              <RadioChoice value="cles" label={clesLabel} name="measure" current={measure} action={updateMeasure} />
            </div>
          </div>
        )}
        <label className="sp-margin-m-top">Allowed Effects</label>
        <p>
          If the canary surpasses the baseline by the supplied (non-zero) percentage, the <em>metric</em> will fail.
        </p>
        <div className="horizontal">
          {direction !== 'increase' && buildInput('allowedDecrease', allowedDecrease)}
          {direction !== 'decrease' && buildInput('allowedIncrease', allowedIncrease)}
        </div>
        <label className="sp-margin-m-top">Critical Effects</label>
        <p>
          If the canary surpasses the baseline by the supplied (non-zero) percentage, the <em>entire analysis</em> will
          fail with a score of 0.
        </p>
        <div className="horizontal">
          {direction !== 'increase' && buildInput('criticalDecrease', criticalDecrease)}
          {direction !== 'decrease' && buildInput('criticalIncrease', criticalIncrease)}
        </div>
      </div>
    </FormRow>
  );
}

function mapStateToProps(state: ICanaryState): IEditMetricEffectSizesProps {
  return {
    metricId: state.selectedConfig.editingMetric.id,
    direction: state.selectedConfig.editingMetric.analysisConfigurations?.canary?.direction ?? 'either',
    disabled: state.app.disableConfigEdit,
    effectSizes: state.selectedConfig.editingMetric.analysisConfigurations?.canary?.effectSize ?? {},
  };
}

function mapDispatchToProps(dispatch: any): IEditMetricEffectSizesDispatchProps {
  return {
    updateEffectSize: (id: string, value: ICanaryMetricEffectSizeConfig) =>
      dispatch(Creators.updateEffectSize({ id, value })),
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(EditMetricEffectSizes);
