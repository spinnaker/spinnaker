import * as React from 'react';
import { connect } from 'react-redux';
import { Dispatch } from 'redux';
import { ICanaryState } from 'kayenta/reducers';
import { AsyncRequestState } from 'kayenta/reducers/asyncRequest';
import * as Creators from 'kayenta/actions/creators';
import { IGraphiteMetricDescriptor } from './domain/IGraphiteMetricDescriptor';
import { DISABLE_EDIT_CONFIG, DisableableReactTypeahead } from 'kayenta/layout/disableable';
import './typeahead.less';

interface IGraphiteMetricTypeSelectorDispatchProps {
  load: (filter: string) => void;
}

interface IGraphiteMetricTypeSelectorStateProps {
  options: string[];
  loading: boolean;
}

interface IGraphiteMetricTypeSelectorOwnProps {
  value: string;
  onChange: (option: string[]) => void;
}

export const GraphiteMetricTypeSelector = ({
  loading,
  load,
  options,
  value,
  onChange,
}: IGraphiteMetricTypeSelectorDispatchProps &
  IGraphiteMetricTypeSelectorStateProps &
  IGraphiteMetricTypeSelectorOwnProps) => {
  options = options.concat(value);

  return (
    <DisableableReactTypeahead
      options={options}
      isLoading={loading}
      onChange={(option: string[]) => {
        onChange(option);
        load(option[0]);
      }}
      defaultInputValue={value}
      renderMenuItemChildren={option => (
        <a style={{ pointerEvents: 'all', textDecoration: 'none', color: '#000000' }}>{option}</a>
      )}
      placeholder={'Enter at least three characters to search.'}
      onInputChange={input => {
        onChange([input]);
        load(input);
        return input;
      }}
      disabledStateKeys={[DISABLE_EDIT_CONFIG]}
    />
  );
};

export const mapStateToProps = (state: ICanaryState, ownProps: IGraphiteMetricTypeSelectorOwnProps) => {
  const descriptors = state.data.metricsServiceMetadata.data as IGraphiteMetricDescriptor[];
  const options: string[] = descriptors.map(d => d.name);

  return {
    options,
    loading: state.data.metricsServiceMetadata.load === AsyncRequestState.Requesting,
    ...ownProps,
  };
};

export const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>) => {
  return {
    load: (filter: string) => {
      dispatch(Creators.updateGraphiteMetricDescriptorFilter({ filter }));
    },
  };
};

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(GraphiteMetricTypeSelector);
