import React from 'react';

import { IJsonDiff } from '../../../../utils';

export interface IDiffViewProps {
  diff: IJsonDiff;
}

export function DiffView(props: IDiffViewProps) {
  const { diff } = props;

  function scrollBody(e: any): void {
    let line = 1;
    const target = e.target as HTMLElement;
    const currentTarget = e.currentTarget as HTMLElement;
    if (target.getAttribute('data-attr-block-line')) {
      line = parseInt(target.getAttribute('data-attr-block-line'), 10);
    } else {
      line = (e.offsetY / currentTarget.clientHeight) * this.diff.summary.total;
    }
    $('pre.history').animate({ scrollTop: (line - 3) * 15 }, 200);
  }

  return (
    <>
      <pre className="form-control flex-fill diff">
        {diff.details.map((line, index) => {
          return (
            <div data-attr-line={index} className={line.type} key={index}>
              {line.text}
            </div>
          );
        })}
      </pre>
      <div className="summary-nav flex-fill" onClick={scrollBody}>
        {diff.changeBlocks.map((block, index) => {
          return (
            <div
              data-attr-block-line={block.start}
              key={index}
              style={{ height: block.height + '%', top: block.top + '%' }}
              className={'delta ' + block.type}
            />
          );
        })}
      </div>
    </>
  );
}
