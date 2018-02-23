import * as React from 'react';
import { connect } from 'react-redux';
import { ICanaryClassifierThresholdsConfig } from '../domain';

import { ICanaryState } from '../reducers';
import { CanaryScores } from '../components/canaryScores';
import * as Creators from '../actions/creators';

interface ICanaryClassifierThresholdsConfigDispatchProps {
  handleThresholdsChange: (thresholds: { unhealthyScore: string, successfulScore: string }) => void;
}

/*
 * Fields for updating score thresholds.
 */
function ScoreThresholds({ pass, marginal, handleThresholdsChange }: ICanaryClassifierThresholdsConfig & ICanaryClassifierThresholdsConfigDispatchProps) {
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

function mapStateToProps(state: ICanaryState): ICanaryClassifierThresholdsConfig {
  return {
    marginal: state.selectedConfig.thresholds.marginal,
    pass: state.selectedConfig.thresholds.pass,
  };
}

function mapDispatchToProps(dispatch: any): ICanaryClassifierThresholdsConfigDispatchProps {
  return {
    handleThresholdsChange: (thresholds: { unhealthyScore: string, successfulScore: string }) => {
      dispatch(Creators.updateScoreThresholds({
        marginal: parseInt(thresholds.unhealthyScore, 10),
        pass: parseInt(thresholds.successfulScore, 10),
      }));
    }
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(ScoreThresholds);
