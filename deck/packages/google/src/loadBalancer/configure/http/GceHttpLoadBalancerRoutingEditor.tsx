import React from 'react';

import type {
  IGceLoadBalancerDataItem,
  IGceLoadBalancerHostRule,
  IGceLoadBalancerPathRule,
  IGceResourceReference,
} from '../common';

function parseList(value: string): string[] {
  return value
    .split(/[\n,]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function selectedReference(name: string, options: IGceLoadBalancerDataItem[]): IGceResourceReference | undefined {
  if (!name) {
    return undefined;
  }
  return (options.find((option) => option.name === name) || { name }) as IGceResourceReference;
}

export interface IGceHttpLoadBalancerPathRuleEditorProps {
  backendServices: IGceLoadBalancerDataItem[];
  onChange: (pathRule: IGceLoadBalancerPathRule) => void;
  onRemove: () => void;
  pathRule: IGceLoadBalancerPathRule;
}

export function GceHttpLoadBalancerPathRuleEditor({
  backendServices,
  onChange,
  onRemove,
  pathRule,
}: IGceHttpLoadBalancerPathRuleEditorProps): JSX.Element {
  return (
    <fieldset className="gce-http-path-rule">
      <legend>Path rule</legend>
      <label>
        Paths
        <input
          data-testid="path-rule-paths"
          required
          type="text"
          value={pathRule.paths.join(', ')}
          onChange={(event) => onChange({ ...pathRule, paths: parseList(event.target.value) })}
        />
      </label>
      <label>
        Backend service
        <select
          data-testid="path-rule-backend"
          required
          value={pathRule.backendService?.name || ''}
          onChange={(event) =>
            onChange({
              ...pathRule,
              backendService: selectedReference(event.target.value, backendServices),
            })
          }
        >
          <option value="">Select...</option>
          {backendServices.map((service) => (
            <option key={service.name} value={service.name}>
              {service.name}
            </option>
          ))}
        </select>
      </label>
      <button type="button" className="btn btn-sm btn-default" onClick={onRemove}>
        Remove path rule
      </button>
    </fieldset>
  );
}

export interface IGceHttpLoadBalancerHostRuleEditorProps {
  backendServices: IGceLoadBalancerDataItem[];
  hostRule: IGceLoadBalancerHostRule;
  onChange: (hostRule: IGceLoadBalancerHostRule) => void;
  onRemove: () => void;
}

export function GceHttpLoadBalancerHostRuleEditor({
  backendServices,
  hostRule,
  onChange,
  onRemove,
}: IGceHttpLoadBalancerHostRuleEditorProps): JSX.Element {
  return (
    <fieldset className="gce-http-host-rule">
      <legend>Host rule</legend>
      <label>
        Host patterns
        <input
          data-testid="host-rule-patterns"
          type="text"
          value={hostRule.hostPatterns.join(', ')}
          onChange={(event) => onChange({ ...hostRule, hostPatterns: parseList(event.target.value) })}
        />
      </label>
      <label>
        Default backend service
        <select
          data-testid="host-rule-default-backend"
          required
          value={hostRule.pathMatcher.defaultService?.name || ''}
          onChange={(event) =>
            onChange({
              ...hostRule,
              pathMatcher: {
                ...hostRule.pathMatcher,
                defaultService: selectedReference(event.target.value, backendServices),
              },
            })
          }
        >
          {backendServices.map((service) => (
            <option key={service.name} value={service.name}>
              {service.name}
            </option>
          ))}
        </select>
      </label>
      {hostRule.pathMatcher.pathRules.map((pathRule, index) => (
        <GceHttpLoadBalancerPathRuleEditor
          backendServices={backendServices}
          key={index}
          onChange={(updated) =>
            onChange({
              ...hostRule,
              pathMatcher: {
                ...hostRule.pathMatcher,
                pathRules: hostRule.pathMatcher.pathRules.map((current, currentIndex) =>
                  currentIndex === index ? updated : current,
                ),
              },
            })
          }
          onRemove={() =>
            onChange({
              ...hostRule,
              pathMatcher: {
                ...hostRule.pathMatcher,
                pathRules: hostRule.pathMatcher.pathRules.filter((_, currentIndex) => currentIndex !== index),
              },
            })
          }
          pathRule={pathRule}
        />
      ))}
      <button
        type="button"
        className="add-new btn btn-block"
        data-testid="add-path-rule"
        onClick={() =>
          onChange({
            ...hostRule,
            pathMatcher: {
              ...hostRule.pathMatcher,
              pathRules: [...hostRule.pathMatcher.pathRules, { paths: [] }],
            },
          })
        }
      >
        Add path rule
      </button>
      <button type="button" className="btn btn-sm btn-default" onClick={onRemove}>
        Remove host rule
      </button>
    </fieldset>
  );
}
