import * as React from 'react';
import Select, { Option } from 'react-select';
import { Action } from 'redux';
import { connect } from 'react-redux';

import { ICanaryState } from '../reducers/index';
import * as Creators from '../actions/creators';
import FormRow from '../layout/formRow';
import KayentaInput from '../layout/kayentaInput';

interface IJudgeSelectStateProps {
  judgeOptions: Option[];
  selectedJudge: string;
  renderState: JudgeSelectRenderState;
}

interface IJudgeSelectDispatchProps {
  handleJudgeSelect: (option: Option) => void;
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
        <FormRow>
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
        <FormRow>
          <KayentaInput
            type="text"
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
    handleJudgeSelect: (option: Option) => {
      dispatch(Creators.selectJudgeName({ judge: { name: option.value as string } }));
    }
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(JudgeSelect);
