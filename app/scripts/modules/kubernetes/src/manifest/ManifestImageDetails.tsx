import { has, sortBy } from 'lodash';
import React from 'react';

import { HelpField } from '@spinnaker/core';

export interface IContainer {
  name: string;
  image: string;
}

export interface IManifestImageDetailsProps {
  manifest: {
    spec: {
      template: {
        spec: {
          containers: IContainer;
          initContainers: IContainer;
        };
      };
    };
  };
}

// From https://github.com/docker/distribution/blob/master/docs/spec/api.md
// if an image doesn't have a colon, it doesn't have a tag or digest.
const normalizeImage = (image: string): string => {
  if (image && image.includes(':')) {
    return image;
  }
  return `${image}:latest`;
};

export const ManifestImageDetails = ({ manifest }: IManifestImageDetailsProps) => {
  if (!has(manifest, 'spec.template.spec')) {
    // Could still be loading the manifest in a parent component.
    return null;
  }
  const containers = sortBy(manifest.spec.template.spec.containers || [], ['image']);
  const initContainers = sortBy(manifest.spec.template.spec.initContainers || [], ['image']);
  const hasBothContainerTypes = containers.length > 0 && initContainers.length > 0;
  if (containers.length === 0 && initContainers.length === 0) {
    // Not sure if this could happen
    return <span>No images.</span>;
  }

  return (
    <ul>
      {hasBothContainerTypes && <strong>Containers</strong>}
      {containers.map((container) => (
        <li key={container.image} title={normalizeImage(container.image)} className="break-word">
          {normalizeImage(container.image)}{' '}
          <HelpField content={`This is container <strong>${container.name}</strong>'s image.`} />
        </li>
      ))}
      {hasBothContainerTypes && <strong>Init Containers</strong>}
      {initContainers.map((container) => (
        <li key={container.image} className="break-word" title={normalizeImage(container.image)}>
          {normalizeImage(container.image)}{' '}
          <HelpField content={`This is init container <strong>${container.name}</strong>'s image.`} />
        </li>
      ))}
    </ul>
  );
};
