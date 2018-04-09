import * as React from 'react';

export function ServerGroupStageContext(props: { serverGroups: { [region: string]: string[] } }) {
  if (!props.serverGroups) {
    return null;
  }
  return (
    <div className="row">
      <div className="col-md-11">
        <dl className="dl-narrow dl-horizontal">
          <dt>Disabled</dt>
          <dd />
          {Object.keys(props.serverGroups).map(region => {
            return [
              <dt key={`t${region}`}>{region}</dt>,
              <dd key={`d${region}`}>{props.serverGroups[region].join(', ')}</dd>,
            ];
          })}
        </dl>
      </div>
    </div>
  );
}
