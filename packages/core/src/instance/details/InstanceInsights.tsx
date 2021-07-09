import React from 'react';
import { Dropdown } from 'react-bootstrap';

import { IInstance } from '../../domain';
import { logger } from '../../utils';

export interface Insight {
  label: string;
  url: string;
}

export interface IInstanceInsightsProps {
  analytics?: boolean;
  insights: Insight[];
  instance: IInstance;
  title?: string;
}

export const InstanceInsights = ({ analytics, insights, instance, title }: IInstanceInsightsProps) => {
  const logClickEvent = (label: string) => {
    logger.log({
      category: 'Insight Menu (Instance)',
      action: `${label} clicked`,
      data: { label: `${instance.account}/${instance.region}/${instance.name}/${instance.serverGroup}` },
    });
  };

  if (!insights?.length) {
    return null;
  }

  return (
    <div style={{ display: 'inline-block' }}>
      <Dropdown className="dropdown" id="instace-actions-dropdown">
        <Dropdown.Toggle className="btn btn-sm dropdown-toggle">
          <span>{title || 'Insight'}</span>
        </Dropdown.Toggle>
        <Dropdown.Menu>
          {insights.map((insight) => (
            <li
              key={`insight-${insight.label}`}
              id={`insight-action-${insight.label}`}
              onClick={() => (analytics ? logClickEvent(insight.label) : null)}
            >
              <a target="_blank" href={insight.url}>
                {insight.label}
              </a>
            </li>
          ))}
        </Dropdown.Menu>
      </Dropdown>
    </div>
  );
};
