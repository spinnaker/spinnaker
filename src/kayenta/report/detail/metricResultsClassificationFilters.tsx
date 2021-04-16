import * as React from 'react';
import { connect, Dispatch } from 'react-redux';

import * as Creators from '../../actions/creators';
import { ICanaryAnalysisResult, MetricClassificationLabel } from '../../domain';
import { ICanaryState } from '../../reducers';

export interface IMetricFiltersOwnProps {
  results: ICanaryAnalysisResult[];
}

interface IMetricFiltersStateProps {
  metricFilters: MetricClassificationLabel[];
}

interface IMetricFiltersDispatchProps {
  toggle: (classification: MetricClassificationLabel) => void;
}

const MetricFilters = ({
  results,
  metricFilters,
  toggle,
}: IMetricFiltersOwnProps & IMetricFiltersStateProps & IMetricFiltersDispatchProps) => {
  const options = [
    MetricClassificationLabel.Error,
    MetricClassificationLabel.High,
    MetricClassificationLabel.Low,
    MetricClassificationLabel.Nodata,
    MetricClassificationLabel.Pass,
  ]
    .map((classification) => ({
      classification,
      count: results.filter((r) => r.classification === classification).length,
    }))
    .filter((option) => option.count);

  const checkboxes = options.map((o) => (
    <label key={o.classification}>
      <input
        type="checkbox"
        checked={metricFilters.includes(o.classification)}
        onChange={() => toggle(o.classification)}
      />{' '}
      {o.classification} ({o.count}){' '}
    </label>
  ));

  return <div className="metric-filter-options horizontal center">{checkboxes}</div>;
};

const mapStateToProps = (state: ICanaryState): IMetricFiltersStateProps => ({
  metricFilters: state.selectedRun.metricFilters,
});

const mapDispatchToProps = (
  dispatch: Dispatch<ICanaryState>,
  ownProps: IMetricFiltersOwnProps,
): IMetricFiltersOwnProps & IMetricFiltersDispatchProps => ({
  toggle: (classification: MetricClassificationLabel) =>
    dispatch(Creators.toggleMetricClassificationFilter({ classification })),
  ...ownProps,
});

export default connect(mapStateToProps, mapDispatchToProps)(MetricFilters);
