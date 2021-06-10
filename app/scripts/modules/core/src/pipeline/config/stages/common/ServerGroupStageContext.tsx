import React from 'react';

export function ServerGroupStageContext({status = "Disabled", serverGroups}: { status: string, serverGroups: { [region: string]: string[] } }) {
  if (!serverGroups) {
    return null;
  }
  return (
    <div className="row">
      <div className="col-md-11">
        <dl className="dl-narrow dl-horizontal">
          <dt>{status}</dt>
          <dd />
          {Object.keys(serverGroups).map((region) => {
            return [
              <dt key={`t${region}`}>{region}</dt>,
              <dd key={`d${region}`}>{serverGroups[region].join(', ')}</dd>,
            ];
          })}
        </dl>
      </div>
    </div>
  );
}
