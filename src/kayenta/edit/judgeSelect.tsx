import * as React from 'react';
import * as Select from 'react-select';
import { Action } from 'redux';
import { connect } from 'react-redux';

import { ICanaryState } from '../reducers/index';
import { SELECT_JUDGE_NAME } from '../actions/index';
import FormRow from '../layout/formRow';

interface IJudgeSelectStateProps {
  judgeOptions: Select.Option[];
  selectedJudge: string;
  renderState: JudgeSelectRenderState;
}

interface IJudgeSelectDispatchProps {
  handleJudgeSelect: (option: Select.Option) => void;
}

export enum JudgeSelectRenderState {
  Multiple,
  Single,
  None,
}

/*
 * Select field for picking canary judge.
 */
function JudgeSelect({ judgeOptions, selectedJudge, handleJudgeSelect, renderState }: IJudgeSelectStateProps & IJudgeSelectDispatchProps) {
  switch (renderState) {
    case JudgeSelectRenderState.Multiple:
      return (
        <FormRow label="Judge">
          <Select
            value={selectedJudge}
            options={judgeOptions}
            clearable={false}
            onChange={handleJudgeSelect}
          />
        </FormRow>
      );
    case JudgeSelectRenderState.Single:
      return (
        <FormRow label="Judge">
          <input
            type="text"
            className="form-control"
            value={selectedJudge}
            disabled={true}
          />
        </FormRow>
      );
    case JudgeSelectRenderState.None:
      return null;
  }
}

function mapStateToProps(state: ICanaryState): IJudgeSelectStateProps {
  return {
    judgeOptions: (state.data.judges || []).map(judge => ({value: judge.name, label: judge.name})),
    selectedJudge: state.selectedConfig.judge.judgeConfig.name,
    renderState: state.selectedConfig.judge.renderState,
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IJudgeSelectDispatchProps {
  return {
    handleJudgeSelect: (option: Select.Option) => {
      dispatch({
        type: SELECT_JUDGE_NAME,
        judge: { name: option.value },
      });
    }
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(JudgeSelect);
