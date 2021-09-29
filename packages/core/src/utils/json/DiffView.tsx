import React from 'react';

import type { IJsonDiff } from './JsonUtils';

import './DiffView.less';

export interface IDiffViewProps {
  diff: IJsonDiff;
}

export function DiffView(props: IDiffViewProps) {
  const { diff } = props;
  const preRef = React.useRef<HTMLPreElement>();

  function scrollBody(e: any): void {
    let line;
    const target = e.target as HTMLElement;
    const currentTarget = e.currentTarget as HTMLElement;
    const lineHeight = (preRef.current.firstChild as HTMLElement).clientHeight;
    if (target.getAttribute('data-attr-block-line')) {
      line = Number(target.getAttribute('data-attr-block-line'));
    } else {
      line = (e.nativeEvent?.offsetY / currentTarget.clientHeight) * diff.summary.total;
    }
    // scroll into view, leaving three lines above
    preRef.current.scroll({ top: (line - 3) * lineHeight, behavior: 'smooth' });
  }

  return (
    <>
      <pre ref={preRef} className="form-control flex-fill diff">
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
