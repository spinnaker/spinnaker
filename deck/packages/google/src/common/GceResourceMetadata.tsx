import React from 'react';

import { HelpContentsRegistry, HelpField } from '@spinnaker/core';

export const MAX_METADATA_VALUE_LENGTH = 90;

export interface IGceMetadataItem {
  key: string;
  value?: string;
}

/** Renders a help icon only when there is help content to show, so unknown keys do not get empty popovers. */
export function GceOptionalHelpField({ id, content }: { id?: string; content?: string }): JSX.Element {
  if (content) {
    return <HelpField content={content} />;
  }
  if (id && HelpContentsRegistry.getHelpField(id)) {
    return <HelpField id={id} />;
  }
  return null;
}

export function truncateMetadataValue(value: unknown): string {
  if (value === undefined || value === null) {
    return '';
  }
  const stringValue = String(value);
  return stringValue.length > MAX_METADATA_VALUE_LENGTH
    ? `${stringValue.substring(0, MAX_METADATA_VALUE_LENGTH)}...`
    : stringValue;
}

/**
 * Builds a tag -> help text map describing which firewalls in the same account target each network tag.
 */
export function buildGceTagHelpMap(app: any, account: string, tags: string[] = []): Record<string, string> {
  const firewalls: any[] = (app?.securityGroups?.data || []).filter(
    (firewall: any) => firewall.accountName === account && Array.isArray(firewall.targetTags),
  );
  const helpMap: Record<string, string> = {};
  tags.forEach((tag) => {
    const names = firewalls.filter((firewall) => firewall.targetTags.includes(tag)).map((firewall) => firewall.name);
    if (names.length) {
      const groupOrGroups = names.length > 1 ? 'groups' : 'group';
      helpMap[tag] = `This tag associates this resource with security ${groupOrGroups} <em>${names.join(', ')}</em>.`;
    }
  });
  return helpMap;
}

export interface IGceCustomMetadataListProps {
  items?: IGceMetadataItem[];
  helpKeyPrefix: string;
  emptyMessage: string;
}

export function GceCustomMetadataList({
  items,
  helpKeyPrefix,
  emptyMessage,
}: IGceCustomMetadataListProps): JSX.Element {
  if (!items?.length) {
    return <div>{emptyMessage}</div>;
  }
  return (
    <dl>
      {items.map((item) => (
        <React.Fragment key={item.key}>
          <dt>
            {item.key} <GceOptionalHelpField id={`${helpKeyPrefix}.${item.key}`} />
          </dt>
          <dd>{truncateMetadataValue(item.value)}</dd>
        </React.Fragment>
      ))}
    </dl>
  );
}

export interface IGceTagsListProps {
  tags?: string[];
  helpMap?: Record<string, string>;
  emptyMessage: string;
}

export function GceTagsList({ tags, helpMap = {}, emptyMessage }: IGceTagsListProps): JSX.Element {
  if (!tags?.length) {
    return <div>{emptyMessage}</div>;
  }
  return (
    <dl>
      {tags.map((tag) => (
        <dd key={tag}>
          {tag} <GceOptionalHelpField content={helpMap[tag]} />
        </dd>
      ))}
    </dl>
  );
}

export interface IGceLabelsListProps {
  labels?: Record<string, string>;
  helpKeyPrefix: string;
  emptyMessage: string;
}

export function GceLabelsList({ labels, helpKeyPrefix, emptyMessage }: IGceLabelsListProps): JSX.Element {
  const entries = Object.entries(labels || {});
  if (!entries.length) {
    return <div>{emptyMessage}</div>;
  }
  return (
    <dl>
      {entries.map(([key, value]) => (
        <React.Fragment key={key}>
          <dt>
            {key} <GceOptionalHelpField id={`${helpKeyPrefix}.${key}`} />
          </dt>
          <dd>{value}</dd>
        </React.Fragment>
      ))}
    </dl>
  );
}
