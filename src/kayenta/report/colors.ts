import { MetricClassificationLabel } from 'kayenta/domain/MetricClassificationLabel';
import { ScoreClassificationLabel } from 'kayenta/domain/ScoreClassificationLabel';
import { ICanaryJudgeGroupScore } from '../domain/ICanaryJudgeResult';

// Standard Spinnaker styleguide colors.
const GREEN = 'var(--color-success)';
const RED = 'var(--color-danger)';
const GREY = 'var(--color-text-caption)';
const YELLOW = 'var(--color-warning)';

export const mapMetricClassificationToColor = (classification: MetricClassificationLabel): string => ({
    [MetricClassificationLabel.High]: RED,
    [MetricClassificationLabel.Low]: RED,
    [MetricClassificationLabel.Error]: YELLOW,
    [MetricClassificationLabel.Nodata]: GREY,
    [MetricClassificationLabel.Pass]: GREEN,
  }[classification]);

export const mapScoreClassificationToColor = (classification: ScoreClassificationLabel): string => ({
    [ScoreClassificationLabel.Fail]: RED,
    [ScoreClassificationLabel.Error]: YELLOW,
    [ScoreClassificationLabel.Marginal]: GREY,
    [ScoreClassificationLabel.Nodata]: GREY,
    [ScoreClassificationLabel.Pass]: GREEN,
  }[classification]);

export const mapGroupToColor = (group: ICanaryJudgeGroupScore, scoreThresholds: { pass: number, marginal: number }): string => {
  if (typeof group.score !== 'number') {
    return YELLOW; // Some kind of error.
  } else if (group.score > scoreThresholds.pass) {
    return GREEN;
  } else if (group.score > scoreThresholds.marginal) {
    return GREY;
  } else {
    return RED;
  }
};
