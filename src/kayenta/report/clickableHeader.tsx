import * as React from 'react';
import * as classNames from 'classnames';

import ScoreArrow from './scoreArrow';

export interface IGroupScoreProps {
  label: string;
  style?: {[key: string]: string };
  onClick: () => void;
  className: string;
}

/*
* Component for a labeled, clickable, colored header.
* */
export default ({ label, onClick, style, className }: IGroupScoreProps) => (
  <section
    style={style}
    onClick={onClick}
    className={classNames('clickable', 'text-center', 'group-score', className)}
  >
    <h3 className="heading-3 uppercase label">{label}</h3>
    <ScoreArrow borderTopColor={style.backgroundColor}/>
  </section>
);
