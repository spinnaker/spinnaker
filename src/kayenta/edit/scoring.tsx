import * as React from 'react';
import { connect } from 'react-redux';

import TitledSubsection from 'kayenta/layout/titledSubsection';
import GroupWeights from './groupWeights';
import JudgeSelect, { JudgeSelectRenderState } from './judgeSelect';
import ScoreThresholds from './scoreThresholds';
import { ICanaryState } from '../reducers/index';
import FormList from '../layout/formList';

interface IScoringStateProps {
  renderJudgeSelect: boolean;
}

function Scoring({ renderJudgeSelect }: IScoringStateProps) {
  return (
    <FormList>
      <TitledSubsection title="Thresholds">
        <ScoreThresholds/>
      </TitledSubsection>
      {renderJudgeSelect && (
        <TitledSubsection title="Judge">
          <JudgeSelect/>
        </TitledSubsection>
      )}
      <TitledSubsection title="Metric Group Weights">
        <GroupWeights/>
      </TitledSubsection>
    </FormList>
  );
}

function mapStateToProps(state: ICanaryState) {
  return {
    renderJudgeSelect: state.selectedConfig.judge.renderState !== JudgeSelectRenderState.None,
  };
}

export default connect(mapStateToProps)(Scoring);
