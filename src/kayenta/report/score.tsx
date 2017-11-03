import * as React from 'react';
import * as classNames from 'classnames';
import { round } from 'lodash';
import { ICanaryJudgeScore } from '../domain/ICanaryJudgeResult';
import { CanaryScore } from 'kayenta/components/canaryScore';
import { ScoreClassificationLabel } from '../domain/ScoreClassificationLabel';

export interface ICanaryJudgeScoreProps {
  score: ICanaryJudgeScore;
  onClick: () => void;
  className?: string;
}

/*
* Renders top-level canary report score.
*/
export default ({ score, className, onClick }: ICanaryJudgeScoreProps) => (
  <section className={classNames(className, 'clickable')} onClick={onClick}>
    <CanaryScore
      className="score-report"
      score={round(score.score, 2)}
      result={mapKayentaToACA(score).result}
      health={mapKayentaToACA(score).health}
      inverse={false}
    />
  </section>
);

// TODO: don't do this (i.e., stop using the ACA statuses in the CanaryScore component).
const mapKayentaToACA = (score: ICanaryJudgeScore): { health: string, result: string, } => {
  if (score.classification === ScoreClassificationLabel.Pass) {
    return { health: null, result: 'success' };
  } else if (score.classification === ScoreClassificationLabel.Fail) {
    return { health: 'unhealthy', result: null };
  } else {
    return { health: null, result: null };
  }
};
