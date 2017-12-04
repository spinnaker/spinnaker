import * as React from 'react';

const ARROW_CLASS = 'arrow-down';

export interface IScoreArrowProps {
  borderTopColor: string;
}

export default ({ borderTopColor }: IScoreArrowProps) => (
  <div className={ARROW_CLASS} style={{ borderTopColor }}/>
);
