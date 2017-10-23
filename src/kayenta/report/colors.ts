import { MetricClassificationLabel } from 'kayenta/domain/MetricClassificationLabel';
import { ScoreClassificationLabel } from '../domain/ScoreClassificationLabel';

// Standard Spinnaker styleguide colors.
const GREEN = 'var(--color-success)';
const RED = 'var(--color-danger)';
const GREY = 'var(--color-alto)';
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
