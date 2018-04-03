import * as React from 'react';
import { connect } from 'react-redux';

import { ICanaryState } from 'kayenta/reducers';
import { CanaryScores } from 'kayenta/components/canaryScores';
import * as Creators from 'kayenta/actions/creators';

interface ICanaryClassifierThresholdsConfigDispatchProps {
  handleThresholdsChange: (thresholds: { unhealthyScore: string, successfulScore: string }) => void;
}

interface IScoreThresholdStateProps {
  marginal: number;
  pass: number;
  disabled: boolean;
}

/*
 * Fields for updating score thresholds.
 */
function ScoreThresholds({ pass, marginal, disabled, handleThresholdsChange }: IScoreThresholdStateProps & ICanaryClassifierThresholdsConfigDispatchProps) {
  return (
    <CanaryScores
      unhealthyHelpFieldId={'pipeline.config.canary.marginalScore'}
      unhealthyLabel={'Marginal'}
      unhealthyScore={marginal ? marginal.toString() : ''}
      successfulHelpFieldId={'pipeline.config.canary.passingScore'}
      successfulScore={pass ? pass.toString() : ''}
      successfulLabel={'Pass'}
      onChange={handleThresholdsChange}
      disabled={disabled}
    />
  )
}

function mapStateToProps(state: ICanaryState): IScoreThresholdStateProps {
  return {
    marginal: state.selectedConfig.thresholds.marginal,
    pass: state.selectedConfig.thresholds.pass,
    disabled: state.app.disableConfigEdit,
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
