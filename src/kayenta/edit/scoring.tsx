import TitledSubsection from 'kayenta/layout/titledSubsection';
import * as React from 'react';
import { connect } from 'react-redux';

import GroupWeights from './groupWeights';
import JudgeSelect, { JudgeSelectRenderState } from './judgeSelect';
import FormList from '../layout/formList';
import { ICanaryState } from '../reducers/index';

interface IScoringStateProps {
  renderJudgeSelect: boolean;
}

function Scoring({ renderJudgeSelect }: IScoringStateProps) {
  return (
    <FormList>
      {renderJudgeSelect && (
        <TitledSubsection title="Judge">
          <JudgeSelect />
        </TitledSubsection>
      )}
      <TitledSubsection title="Metric Group Weights" helpKey="canary.config.metricGroupWeights">
        <GroupWeights />
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
