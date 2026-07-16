import React from 'react';

import type { IStageConfigProps } from '@spinnaker/core';
import { AccountService, AuthenticationService, HelpField } from '@spinnaker/core';

import { CanaryAnalysisNameSelector } from '../canary/CanaryAnalysisNameSelector';

function parseNotificationHours(value: string) {
  return (value || '').split(',').map((item) => {
    const parsed = parseInt(item.trim(), 10);
    return !isNaN(parsed) ? parsed : 0;
  });
}

function updateWatchers(stage: any, recipients: string) {
  if (recipients.includes('${')) {
    stage.canary.watchers = recipients;
  } else {
    stage.canary.watchers = recipients.split(',').map((email) => email.trim());
  }
}

export function AcaTaskStageConfig(props: IStageConfigProps) {
  const { application, pipeline, stage, stageFieldUpdated } = props;
  const user = AuthenticationService.getAuthenticatedUser();
  stage.baseline = stage.baseline || {};
  stage.canary = stage.canary || {};
  stage.canary.application = stage.canary.application || application.name;
  stage.canary.owner = stage.canary.owner || (user.authenticated ? user.name : null);
  stage.canary.watchers = stage.canary.watchers || [];
  stage.canary.canaryConfig = stage.canary.canaryConfig || { name: [pipeline.name, 'Canary'].join(' - ') };
  stage.canary.canaryConfig.canaryHealthCheckHandler = Object.assign(
    stage.canary.canaryConfig.canaryHealthCheckHandler || {},
    {
      '@class': 'com.netflix.spinnaker.mine.CanaryResultHealthCheckHandler',
    },
  );
  stage.canary.canaryConfig.canarySuccessCriteria = stage.canary.canaryConfig.canarySuccessCriteria || {};
  stage.canary.canaryConfig.canaryAnalysisConfig = stage.canary.canaryConfig.canaryAnalysisConfig || {};
  stage.canary.canaryConfig.canaryAnalysisConfig.notificationHours =
    stage.canary.canaryConfig.canaryAnalysisConfig.notificationHours || [];
  stage.canary.canaryConfig.canaryAnalysisConfig.useLookback =
    stage.canary.canaryConfig.canaryAnalysisConfig.useLookback || false;
  stage.canary.canaryConfig.canaryAnalysisConfig.lookbackMins =
    stage.canary.canaryConfig.canaryAnalysisConfig.lookbackMins || 0;
  stage.canary.canaryDeployments = stage.canary.canaryDeployments || [
    { type: 'query', '@class': '.CanaryTaskDeployment' },
  ];

  const canaryDeployment = stage.canary.canaryDeployments[0];
  const [accounts, setAccounts] = React.useState<any[]>([]);
  const [regions, setRegions] = React.useState<string[]>([]);
  const [recipients, setRecipients] = React.useState(
    stage.canary.watchers
      ? Array.isArray(stage.canary.watchers)
        ? stage.canary.watchers.join(', ')
        : stage.canary.watchers
      : '',
  );
  const [notificationHours, setNotificationHours] = React.useState(
    stage.canary.canaryConfig.canaryAnalysisConfig.notificationHours.join(','),
  );

  React.useEffect(() => {
    const providers = application.attributes.cloudProviders.length ? application.attributes.cloudProviders : ['aws'];
    providers.forEach((provider: string) => {
      AccountService.listAccounts(provider).then((result) => setAccounts((current) => current.concat(result)));
      AccountService.getUniqueAttributeForAllAccounts(provider, 'regions').then((result: string[]) =>
        setRegions((current) => current.concat(result)),
      );
    });
  }, [application]);

  const update = (updater: () => void) => {
    updater();
    stageFieldUpdated();
  };

  return (
    <div className="form-horizontal canary-config-view">
      <h4>Canary Config</h4>
      <StageField label="Name">
        <input
          type="text"
          required={true}
          value={stage.canary.canaryConfig.name || ''}
          className="form-control input-sm"
          onChange={(e) => update(() => (stage.canary.canaryConfig.name = e.target.value))}
        />
      </StageField>
      <StageField label="Canary Lifetime">
        <input
          type="text"
          required={true}
          value={stage.canary.canaryConfig.lifetimeHours || ''}
          className="form-control input-sm"
          style={{ display: 'inline-block', width: '33%' }}
          onChange={(e) => update(() => (stage.canary.canaryConfig.lifetimeHours = e.target.value))}
        />{' '}
        <span className="form-control-static">hours</span>
      </StageField>
      <StageField label="Result Strategy">
        <select
          className="form-control input-sm"
          value={stage.canary.canaryConfig.combinedCanaryResultStrategy || ''}
          onChange={(e) => update(() => (stage.canary.canaryConfig.combinedCanaryResultStrategy = e.target.value))}
        >
          <option value="LOWEST">lowest</option>
          <option value="AGGREGATE">average</option>
        </select>
      </StageField>
      <div className="form-group">
        <div className="col-md-2 col-md-offset-1 sm-label-right">
          <label>Successful Score</label> <HelpField id="pipeline.config.canary.successfulScore" />
        </div>
        <div className="col-md-1">
          <input
            type="text"
            required={true}
            value={stage.canary.canaryConfig.canarySuccessCriteria.canaryResultScore || ''}
            className="form-control input-sm"
            onChange={(e) =>
              update(() => (stage.canary.canaryConfig.canarySuccessCriteria.canaryResultScore = e.target.value))
            }
          />
        </div>
        <div className="col-md-2 col-md-offset-1 sm-label-right">
          <label>Unhealthy Score</label> <HelpField id="pipeline.config.canary.unhealthyScore" />
        </div>
        <div className="col-md-1">
          <input
            type="text"
            required={true}
            value={stage.canary.canaryConfig.canaryHealthCheckHandler.minimumCanaryResultScore || ''}
            className="form-control input-sm"
            onChange={(e) =>
              update(
                () => (stage.canary.canaryConfig.canaryHealthCheckHandler.minimumCanaryResultScore = e.target.value),
              )
            }
          />
        </div>
      </div>
      <div className="checkbox col-md-offset-1">
        <label>
          <input
            type="checkbox"
            checked={!!stage.continueOnUnhealthy}
            onChange={(e) => update(() => (stage.continueOnUnhealthy = e.target.checked))}
          />{' '}
          <b>Continue on Unhealthy</b> <HelpField id="pipeline.config.canary.continueOnUnhealthy" />
        </label>
      </div>

      <h5>Analysis Config</h5>
      <div className="horizontal-rule" />
      <StageField label="Name">
        <CanaryAnalysisNameSelector
          value={stage.canary.canaryConfig.canaryAnalysisConfig.name || ''}
          className="form-control input-sm"
          onChange={(value) => update(() => (stage.canary.canaryConfig.canaryAnalysisConfig.name = value))}
        />
      </StageField>
      <StageField label="Delay">
        <input
          type="text"
          required={true}
          value={stage.canary.canaryConfig.canaryAnalysisConfig.beginCanaryAnalysisAfterMins || ''}
          className="form-control input-sm"
          style={{ display: 'inline-block', width: '20%' }}
          onChange={(e) =>
            update(() => (stage.canary.canaryConfig.canaryAnalysisConfig.beginCanaryAnalysisAfterMins = e.target.value))
          }
        />{' '}
        <span className="form-control-static">
          minutes before starting analysis <HelpField id="pipeline.config.canary.delayBeforeAnalysis" />
        </span>
      </StageField>
      <StageField label="Notification Hours">
        <input
          type="text"
          value={notificationHours}
          className="form-control input-sm"
          onChange={(e) => {
            setNotificationHours(e.target.value);
            update(
              () =>
                (stage.canary.canaryConfig.canaryAnalysisConfig.notificationHours = parseNotificationHours(
                  e.target.value,
                )),
            );
          }}
        />
      </StageField>
      <StageField label="Frequency">
        <input
          type="text"
          required={true}
          value={stage.canary.canaryConfig.canaryAnalysisConfig.canaryAnalysisIntervalMins || ''}
          className="form-control input-sm"
          style={{ width: '33%', display: 'inline-block' }}
          onChange={(e) =>
            update(() => (stage.canary.canaryConfig.canaryAnalysisConfig.canaryAnalysisIntervalMins = e.target.value))
          }
        />{' '}
        <span className="form-control-static"> minutes</span>
      </StageField>
      <div className="checkbox col-md-offset-1">
        <label>
          <input
            type="checkbox"
            checked={!!stage.canary.canaryConfig.canaryAnalysisConfig.useLookback}
            onChange={(e) =>
              update(() => (stage.canary.canaryConfig.canaryAnalysisConfig.useLookback = e.target.checked))
            }
          />{' '}
          <b>Use Look-back</b> <HelpField id="pipeline.config.canary.lookback" />
        </label>
      </div>
      {stage.canary.canaryConfig.canaryAnalysisConfig.useLookback && (
        <StageField label="Look-back Duration">
          <input
            type="number"
            required={true}
            value={stage.canary.canaryConfig.canaryAnalysisConfig.lookbackMins || ''}
            className="form-control input-sm"
            style={{ display: 'inline-block', width: '40%' }}
            onChange={(e) =>
              update(() => (stage.canary.canaryConfig.canaryAnalysisConfig.lookbackMins = Number(e.target.value)))
            }
          />{' '}
          <span className="form-control-static"> minutes</span>
        </StageField>
      )}

      <h5>Owner</h5>
      <div className="horizontal-rule" />
      <StageField label="Email">
        <input
          type="email"
          required={true}
          value={stage.canary.owner || ''}
          className="form-control input-sm"
          onChange={(e) => update(() => (stage.canary.owner = e.target.value))}
        />
      </StageField>
      <h5>Watchers</h5>
      <div className="horizontal-rule" />
      <StageField label="Emails">
        <textarea
          value={recipients}
          className="form-control input-sm"
          onChange={(e) => {
            setRecipients(e.target.value);
            update(() => updateWatchers(stage, e.target.value));
          }}
        />
      </StageField>

      <h5>
        Metric Scope <HelpField id="pipeline.config.canary.baselineVersion" />
      </h5>
      <div className="horizontal-rule" />
      <StageField label="Account">
        <select
          required={true}
          className="form-control input-sm"
          value={canaryDeployment.accountName || ''}
          onChange={(e) => update(() => (canaryDeployment.accountName = e.target.value))}
        >
          <option value="" />
          {accounts.map((account) => (
            <option key={account.name || account} value={account.name || account}>
              {account.name || account}
            </option>
          ))}
        </select>
      </StageField>
      <StageField label="Region">
        {regions.map((region) => (
          <label className="checkbox-inline" key={region}>
            <input
              type="radio"
              value={region}
              checked={canaryDeployment.region === region}
              onChange={() => update(() => (canaryDeployment.region = region))}
            />{' '}
            {region}
          </label>
        ))}
      </StageField>
      <StageField label="Scope Type">
        <select
          name="scopeType"
          className="form-control input-sm"
          value={canaryDeployment.type || 'query'}
          onChange={(e) => update(() => (canaryDeployment.type = e.target.value))}
        >
          <option value="query">Query</option>
          <option value="cluster">Cluster</option>
          <option value="asg">ASG</option>
        </select>
      </StageField>
      <StageField label="Baseline">
        {canaryDeployment.type === 'query' ? (
          <textarea
            className="form-control input-sm"
            value={canaryDeployment.baseline || ''}
            onChange={(e) => update(() => (canaryDeployment.baseline = e.target.value))}
          />
        ) : (
          <input
            className="form-control input-sm"
            value={canaryDeployment.baseline || ''}
            required={true}
            type="text"
            onChange={(e) => update(() => (canaryDeployment.baseline = e.target.value))}
          />
        )}
      </StageField>
      <StageField label="Canary">
        {canaryDeployment.type === 'query' ? (
          <textarea
            className="form-control input-sm"
            value={canaryDeployment.canary || ''}
            onChange={(e) => update(() => (canaryDeployment.canary = e.target.value))}
          />
        ) : (
          <input
            className="form-control input-sm"
            value={canaryDeployment.canary || ''}
            required={true}
            type="text"
            onChange={(e) => update(() => (canaryDeployment.canary = e.target.value))}
          />
        )}
      </StageField>
    </div>
  );
}

function StageField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="form-group">
      <div className="col-md-2 col-md-offset-1 sm-label-right">
        <label>{label}</label>
      </div>
      <div className="col-md-9">{children}</div>
    </div>
  );
}
