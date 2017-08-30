import * as React from 'react';
import { connect } from 'react-redux';

import { ICanaryState } from '../reducers';
import { CanaryScores } from '../components/canaryScores';
import { UPDATE_SCORE_THRESHOLDS } from '../actions/index';

interface IScoreThresholdsStateProps {
  pass: number;
  marginal: number;
}

interface IScoreThresholdsDispatchProps {
  handleThresholdsChange: (thresholds: { unhealthyScore: string, successfulScore: string }) => void;
}

/*
 * Fields for updating score thresholds.
 */
function ScoreThresholds({ pass, marginal, handleThresholdsChange }: IScoreThresholdsStateProps & IScoreThresholdsDispatchProps) {
  return (
    <CanaryScores
      unhealthyHelpFieldId={'pipeline.config.canary.marginalScore'}
      unhealthyLabel={'Marginal'}
      unhealthyScore={marginal ? marginal.toString() : ''}
      successfulHelpFieldId={'pipeline.config.canary.passingScore'}
      successfulScore={pass ? pass.toString() : ''}
      successfulLabel={'Pass'}
      onChange={handleThresholdsChange}
    />
  )
}

function mapStateToProps(state: ICanaryState): IScoreThresholdsStateProps {
  return {
    marginal: state.selectedConfig.thresholds.marginal,
    pass: state.selectedConfig.thresholds.pass,
  };
}

function mapDispatchToProps(dispatch: any): IScoreThresholdsDispatchProps {
  return {
    handleThresholdsChange: (thresholds: { unhealthyScore: string, successfulScore: string }) => {
      dispatch({
        type: UPDATE_SCORE_THRESHOLDS,
        marginal: parseInt(thresholds.unhealthyScore, 10),
        pass: parseInt(thresholds.successfulScore, 10),
      });
    }
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(ScoreThresholds);
