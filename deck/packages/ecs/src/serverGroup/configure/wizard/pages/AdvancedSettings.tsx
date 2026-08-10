import React from 'react';

import { HelpField, MapEditor } from '@spinnaker/core';

import type { IEcsWizardPageProps } from './common';

const placementStrategies = [
  'AZ Balanced Spread',
  'AZ Balanced BinPack CPU',
  'AZ Balanced BinPack Memory',
  'BinPack CPU',
  'BinPack Memory',
  'One Task Per Host',
  'None',
];

export const AdvancedSettings = ({ command, onFieldChange }: IEcsWizardPageProps) => {
  const placementConstraints = command.placementConstraints || [];
  const iamRoles = Array.from(new Set([...(command.backingData.filtered.iamRoles || []), command.iamRole])).filter(
    Boolean,
  );
  const secrets = Array.from(
    new Set([...(command.backingData.filtered.secrets || []), command.dockerImageCredentialsSecret]),
  ).filter(Boolean);
  const updateConstraint = (index: number, field: string, value: string) => {
    const constraints = placementConstraints.map((constraint: any, constraintIndex: number) =>
      constraintIndex === index ? { ...constraint, [field]: value } : constraint,
    );
    onFieldChange('placementConstraints', constraints);
  };

  return (
    <div className="container-fluid form-horizontal" data-test-id="EcsServerGroupWizard.advancedSettings">
      <div className="form-group">
        <div className="col-md-5 sm-label-right">
          Health Check Grace Period <HelpField id="ecs.healthgraceperiod" />
        </div>
        <div className="col-md-2">
          <input
            aria-label="Health check grace period"
            className="form-control input-sm no-spel"
            data-test-id="Advanced.healthCheckGracePeriodSeconds"
            onChange={(event) =>
              onFieldChange(
                'healthCheckGracePeriodSeconds',
                event.target.value === '' ? '' : Number(event.target.value),
              )
            }
            type="number"
            value={command.healthCheckGracePeriodSeconds ?? ''}
          />
        </div>
      </div>

      <div className="form-group">
        <div className="col-md-5 sm-label-right">
          <b>ECS IAM Instance Profile</b> <HelpField id="ecs.iamrole" />
        </div>
        <div className="col-md-7">
          {iamRoles.length ? (
            <select
              aria-label="ECS IAM instance profile"
              className="form-control input-sm"
              data-test-id="Advanced.iamRole"
              onChange={(event) => onFieldChange('iamRole', event.target.value)}
              value={command.iamRole || ''}
            >
              {iamRoles.map((iamRole) => (
                <option key={iamRole} value={iamRole}>
                  {iamRole}
                </option>
              ))}
            </select>
          ) : (
            'No account was selected, or no IAM roles are available for ECS tasks in this account.'
          )}
        </div>
      </div>

      {!command.useTaskDefinitionArtifact && (
        <div className="form-group">
          <div className="col-md-5 sm-label-right">
            <b>Docker Image Credentials</b> <HelpField id="ecs.dockerimagecredentials" />
          </div>
          <div className="col-md-7">
            {secrets.length ? (
              <select
                aria-label="Docker image credentials"
                className="form-control input-sm"
                data-test-id="Advanced.dockerImageCredentialsSecret"
                onChange={(event) => onFieldChange('dockerImageCredentialsSecret', event.target.value)}
                value={command.dockerImageCredentialsSecret || ''}
              >
                {secrets.map((secret) => (
                  <option key={secret} value={secret}>
                    {secret}
                  </option>
                ))}
              </select>
            ) : (
              'No account or region was selected, or no AWS Secrets Manager secrets are available in this account and region.'
            )}
          </div>
        </div>
      )}

      <div className="form-group">
        <div className="col-md-5 sm-label-right">
          Fargate platform version <HelpField id="ecs.platformVersion" />
        </div>
        <div className="col-md-3">
          <input
            aria-label="Fargate platform version"
            className="form-control input-sm"
            data-test-id="Advanced.platformVersion"
            onChange={(event) => onFieldChange('platformVersion', event.target.value)}
            type="text"
            value={command.platformVersion || ''}
          />
        </div>
      </div>

      <div className="form-group">
        <div className="col-md-5 sm-label-right">
          Enable Deployment Circuit Breaker <HelpField id="ecs.enableDeploymentCircuitBreaker" />
        </div>
        <div className="col-md-3">
          <input
            aria-label="Enable deployment circuit breaker"
            checked={!!command.enableDeploymentCircuitBreaker}
            data-test-id="Advanced.enableDeploymentCircuitBreaker"
            onChange={(event) => onFieldChange('enableDeploymentCircuitBreaker', event.target.checked)}
            type="checkbox"
          />
        </div>
      </div>

      <div className="form-group">
        <div className="col-md-5 sm-label-right">
          <b>Placement Strategy</b> <HelpField id="ecs.placementStrategy" />
        </div>
        <div className="col-md-7">
          <select
            aria-label="Placement strategy"
            className="form-control input-sm"
            data-test-id="Advanced.placementStrategyName"
            onChange={(event) => onFieldChange('placementStrategyName', event.target.value)}
            value={command.placementStrategyName || ''}
          >
            <option value="">Select...</option>
            {placementStrategies.map((strategy) => (
              <option key={strategy} value={strategy}>
                {strategy}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="form-group">
        <div className="sm-label-left">
          <b>Placement Constraints</b> <HelpField id="ecs.placementConstraints" />
        </div>
        <table className="table table-condensed packed tags">
          <thead>
            <tr>
              <th style={{ width: '25%' }}>Type</th>
              <th style={{ width: '68%' }}>Expression</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {placementConstraints.map((constraint: any, index: number) => (
              <tr key={index}>
                <td>
                  <select
                    aria-label={`Placement constraint type ${index + 1}`}
                    className="form-control input-sm"
                    data-test-id={`Advanced.placementConstraint.type.${index}`}
                    onChange={(event) => updateConstraint(index, 'type', event.target.value)}
                    value={constraint.type || ''}
                  >
                    <option value="">Select...</option>
                    <option value="distinctInstance">distinctInstance</option>
                    <option value="memberOf">memberOf</option>
                  </select>
                </td>
                <td>
                  <input
                    aria-label={`Placement constraint expression ${index + 1}`}
                    className="form-control input-sm no-spel"
                    data-test-id={`Advanced.placementConstraint.expression.${index}`}
                    onChange={(event) => updateConstraint(index, 'expression', event.target.value)}
                    type="text"
                    value={constraint.expression || ''}
                  />
                </td>
                <td>
                  <button
                    aria-label={`Remove placement constraint ${index + 1}`}
                    className="btn btn-link sm-label"
                    onClick={() =>
                      onFieldChange(
                        'placementConstraints',
                        placementConstraints.filter(
                          (_constraint: any, constraintIndex: number) => constraintIndex !== index,
                        ),
                      )
                    }
                    type="button"
                  >
                    <span className="glyphicon glyphicon-trash" /> <span className="sr-only">Remove</span>
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan={3}>
                <button
                  className="btn btn-block btn-sm add-new"
                  onClick={() => onFieldChange('placementConstraints', [...placementConstraints, {}])}
                  type="button"
                >
                  <span className="glyphicon glyphicon-plus-sign" /> Add New Placement Constraint
                </button>
              </td>
            </tr>
          </tfoot>
        </table>
      </div>

      {!command.useTaskDefinitionArtifact && (
        <>
          <div className="form-group" data-test-id="Advanced.dockerLabels">
            <div className="sm-label-left">
              <b>Docker labels (optional)</b> <HelpField id="ecs.dockerLabels" />
            </div>
            <MapEditor
              allowEmpty={true}
              keyLabel="Docker label name"
              model={command.dockerLabels || {}}
              onChange={(dockerLabels) => onFieldChange('dockerLabels', dockerLabels)}
              valueLabel="Docker label value"
            />
          </div>
          <div className="form-group" data-test-id="Advanced.environmentVariables">
            <div className="sm-label-left">
              <b>Environment Variables (optional)</b> <HelpField id="ecs.environmentVariables" />
            </div>
            <MapEditor
              allowEmpty={true}
              keyLabel="Environment variable name"
              model={command.environmentVariables || {}}
              onChange={(environmentVariables) => onFieldChange('environmentVariables', environmentVariables)}
              valueLabel="Environment variable value"
            />
          </div>
        </>
      )}

      <div className="form-group" data-test-id="Advanced.tags">
        <div className="sm-label-left">
          <b>Tags (optional)</b> <HelpField id="ecs.tags" />
        </div>
        <MapEditor
          allowEmpty={true}
          keyLabel="Tag name"
          model={command.tags || {}}
          onChange={(tags) => onFieldChange('tags', tags)}
          valueLabel="Tag value"
        />
      </div>

      {command.useTaskDefinitionArtifact && (
        <div style={{ color: '#666' }}>
          <hr />
          <p>
            <em>
              <strong>Docker Image Credentials, Docker labels</strong>, and <strong>Environment Variables</strong>{' '}
              cannot be individually set when using a Task Definition artifact. Please include them in their respective
              container definition in that file.
            </em>
          </p>
        </div>
      )}
    </div>
  );
};
