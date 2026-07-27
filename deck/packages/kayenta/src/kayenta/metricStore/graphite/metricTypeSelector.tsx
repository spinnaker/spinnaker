import * as React from 'react';
import { connect } from 'react-redux';
import type { Dispatch } from 'redux';

import * as Creators from '../../actions/creators';
import type { IGraphiteMetricDescriptor } from './domain/IGraphiteMetricDescriptor';
import { DISABLE_EDIT_CONFIG, DisableableReactTypeahead } from '../../layout/disableable';
import type { ICanaryState } from '../../reducers';
import { AsyncRequestState } from '../../reducers/asyncRequest';

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
      renderMenuItemChildren={(option) => (
        <a style={{ pointerEvents: 'all', textDecoration: 'none', color: '#000000' }}>{option}</a>
      )}
      placeholder={'Enter at least three characters to search.'}
      onInputChange={(input) => {
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
  const options: string[] = descriptors.map((d) => d.name);

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

export default connect(mapStateToProps, mapDispatchToProps)(GraphiteMetricTypeSelector);
