import DOMPurify from 'dompurify';
import { get, partition } from 'lodash';
import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';

const PATH_EXPRESSION_PATTERN = /^[A-Za-z_$][\w$]*(?:(?:\.[A-Za-z_$][\w$]*)|\[(?:\d+|'[^']+'|"[^"]+")\])*$/;
const BLOCKED_PATH_SEGMENT_PATTERN = /(?:^|\.)(?:constructor|__proto__|prototype)(?=\.|\[|$)|\[(?:"(?:constructor|__proto__|prototype)"|'(?:constructor|__proto__|prototype)')\]/;

export interface IAnnotationCustomSectionsProps {
  manifest: any;
  resource: any;
}

interface IAnnotationsMap {
  [key: string]: string;
}

interface ICustomSection {
  title: string;
  key: string;
  isHtml: boolean;
  content: string;
}

interface ICustomSectionMap {
  [key: string]: ICustomSection[];
}

interface IParsedAnnotationKey {
  title: string;
  key: string;
  isHtml: boolean;
}

export function AnnotationCustomSections({ manifest, resource }: IAnnotationCustomSectionsProps) {
  const customSections = buildAnnotationSections(manifest?.metadata?.annotations, resource, manifest);

  return (
    <>
      {Object.entries(customSections).map(([section, entries]) => (
        <CollapsibleSection key={section} heading={section} defaultExpanded={true}>
          {entries.map((entry, index) =>
            entry.isHtml ? (
              <div
                key={`${entry.key}-${index}`}
                dangerouslySetInnerHTML={{
                  __html: entry.content,
                }}
              />
            ) : (
              <div key={`${entry.key}-${index}`}>
                {entry.key && <span style={{ fontWeight: 'bold' }}>{entry.key}</span>} <span>{entry.content}</span>
              </div>
            ),
          )}
        </CollapsibleSection>
      ))}
    </>
  );
}

function buildAnnotationSections(
  annotations: IAnnotationsMap | undefined,
  resource: any,
  manifest: any,
): ICustomSectionMap {
  if (annotations == null) {
    return {};
  }

  const customSections = Object.keys(annotations).reduce((memo: ICustomSectionMap, annotationKey: string) => {
    const entry = annotationToEntry(annotations[annotationKey], annotationKey, resource, manifest);
    if (entry != null && entry.title) {
      memo[entry.title] = memo[entry.title] || [];
      memo[entry.title].push(entry);
    }
    return memo;
  }, {});

  return Object.keys(customSections).reduce((memo: ICustomSectionMap, sectionTitle: string) => {
    const [htmlEntries, textEntries] = partition(customSections[sectionTitle], (section) => section.isHtml);
    memo[sectionTitle] = textEntries.concat(htmlEntries);
    return memo;
  }, {});
}

function annotationToEntry(
  annotationContent: string,
  annotationKey: string,
  resource: any,
  manifest: any,
): ICustomSection | null {
  const parsed = parseAnnotationKey(annotationKey);
  if (parsed == null) {
    return null;
  }

  const content =
    resource && annotationContent.includes('{{')
      ? interpolateAnnotation(annotationContent, resource, manifest, parsed.isHtml)
      : annotationContent;

  return {
    title: parsed.title.replace(/-/g, ' ').trim(),
    key: parsed.key.replace(/-/g, ' ').trim(),
    content: parsed.isHtml ? sanitizeContent(content) : content,
    isHtml: parsed.isHtml,
  };
}

function parseAnnotationKey(annotationKey: string): IParsedAnnotationKey | null {
  const keyParts = /([^.]+)\.details\.(html\.)?spinnaker\.io(?:\/(.*))?/.exec(annotationKey);
  if (keyParts == null || keyParts.length !== 4) {
    return null;
  }
  return {
    title: keyParts[1] || '',
    key: keyParts[3] || '',
    isHtml: !!keyParts[2],
  };
}

function interpolateAnnotation(content: string, resource: any, manifest: any, escapeValues = false): string {
  const context = { ...resource, resource, manifest };
  return content.replace(/{{\s*([^}]+?)\s*}}/g, (match, path: string) => {
    const expression = path.trim();
    if (!isPathExpression(expression)) {
      return match;
    }

    const value = get(context, expression);
    if (value == null || typeof value === 'function') {
      return '';
    }

    const stringValue = String(value);
    return escapeValues ? escapeHtml(stringValue) : stringValue;
  });
}

function isPathExpression(expression: string): boolean {
  return PATH_EXPRESSION_PATTERN.test(expression) && !BLOCKED_PATH_SEGMENT_PATTERN.test(expression);
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => {
    switch (character) {
      case '&':
        return '&amp;';
      case '<':
        return '&lt;';
      case '>':
        return '&gt;';
      case '"':
        return '&quot;';
      case "'":
        return '&#39;';
      default:
        return character;
    }
  });
}

function sanitizeContent(unsanitized: string): string {
  const sanitized = DOMPurify.sanitize(unsanitized, {
    ADD_ATTR: ['target'],
  });
  const container = document.createElement('div');
  container.innerHTML = sanitized;
  container.querySelectorAll('[target]').forEach((element) => {
    if (element.tagName !== 'A') {
      element.removeAttribute('target');
      return;
    }

    const rel = new Set((element.getAttribute('rel') || '').split(/\s+/).filter(Boolean));
    rel.add('noopener');
    rel.add('noreferrer');
    element.setAttribute('rel', Array.from(rel).join(' '));
  });
  return container.innerHTML;
}
