import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';
import FormRow from 'kayenta/layout/formRow';
import { DisableableInput, DISABLE_EDIT_CONFIG } from 'kayenta/layout/disableable';
import { ICanaryState } from 'kayenta/reducers';
import * as Creators from 'kayenta/actions/creators';
import { get } from 'lodash';
import { ICanaryMetricConfig } from 'kayenta/domain';

interface INewRelicMetricConfigurerStateProps {
  editingMetric: ICanaryMetricConfig;
}

interface INewRelicMetricConfigurerDispatchProps {
  changeSelect: (event: any) => void;
}

type INewRelicMetricConfigurerProps = INewRelicMetricConfigurerStateProps & INewRelicMetricConfigurerDispatchProps;

export const queryFinder = (metric: ICanaryMetricConfig) => get(metric, 'query.select', '');

/*
 * Component for configuring a New Relic metric
 */
function NewRelicMetricConfigurer({ changeSelect, editingMetric }: INewRelicMetricConfigurerProps) {
  return (
    <>
      <FormRow label="NRQL Select">
        <DisableableInput
          type="text"
          value={queryFinder(editingMetric)}
          onChange={changeSelect}
          disabledStateKeys={[DISABLE_EDIT_CONFIG]}
        />
        <span className="body-small color-text-caption" style={{ marginTop: '5px' }}>
          Enter the NRQL query only up to, but not including, the WHERE clause
        </span>
      </FormRow>
    </>
  );
}

function mapStateToProps(state: ICanaryState): INewRelicMetricConfigurerStateProps {
  return {
    editingMetric: state.selectedConfig.editingMetric,
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): INewRelicMetricConfigurerDispatchProps {
  return {
    changeSelect: (event: any) => {
      dispatch(Creators.updateNewRelicSelect({ select: event.target.value }));
    },
  };
}

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(NewRelicMetricConfigurer);
