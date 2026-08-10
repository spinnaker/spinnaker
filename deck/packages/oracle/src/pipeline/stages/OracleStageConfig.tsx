import React, { useEffect } from 'react';

export function OracleStageConfig({ stage, updateStageField }: any) {
  useEffect(() => {
    const defaults: any = {};
    if (!stage.cloudProvider) {
      defaults.cloudProvider = 'oracle';
    }
    if (!stage.cloudProviderType) {
      defaults.cloudProviderType = 'oracle';
    }
    if (!stage.regions) {
      defaults.regions = [];
    }
    if (Object.keys(defaults).length) {
      updateStageField(defaults);
    }
  }, [stage.cloudProvider, stage.cloudProviderType, stage.regions, updateStageField]);

  return (
    <div className="form-horizontal">
      <div className="form-group">
        <div className="col-md-3 sm-label-right">Account</div>
        <div className="col-md-7">
          <input
            className="form-control input-sm"
            onChange={(event) => updateStageField({ credentials: event.target.value })}
            value={stage.credentials || stage.accountName || ''}
          />
        </div>
      </div>
      <div className="form-group">
        <div className="col-md-3 sm-label-right">Region</div>
        <div className="col-md-7">
          <input
            className="form-control input-sm"
            onChange={(event) => updateStageField({ regions: event.target.value ? [event.target.value] : [] })}
            value={(stage.regions && stage.regions[0]) || stage.region || ''}
          />
        </div>
      </div>
    </div>
  );
}
