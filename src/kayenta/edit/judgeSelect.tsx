import * as React from 'react';
import * as Select from 'react-select';
import { Action } from 'redux';
import { connect } from 'react-redux';

import { ICanaryState } from '../reducers/index';
import { SELECT_JUDGE } from '../actions/index';

interface IJudgeSelectStateProps {
  judgeOptions: Select.Option[];
  selectedJudge: string;
}

interface IJudgeSelectDispatchProps {
  handleJudgeSelect: (option: Select.Option) => void;
}

/*
 * Select field for picking canary judge.
 */
function JudgeSelect({ judgeOptions, selectedJudge, handleJudgeSelect }: IJudgeSelectStateProps & IJudgeSelectDispatchProps) {
  return (
    <Select
      value={selectedJudge}
      options={judgeOptions}
      clearable={false}
      onChange={handleJudgeSelect}
    />
  );
}

function mapStateToProps(state: ICanaryState): IJudgeSelectStateProps {
  return {
    judgeOptions: (state.judges || []).map(judge => ({value: judge.name, label: judge.name})),
    selectedJudge: state.selectedJudge.name,
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IJudgeSelectDispatchProps {
  return {
    handleJudgeSelect: (option: Select.Option) => {
      dispatch({
        type: SELECT_JUDGE,
        judge: { name: option.value },
      });
    }
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(JudgeSelect);
