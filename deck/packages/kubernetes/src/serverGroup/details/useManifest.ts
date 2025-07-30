import type { IManifest } from '@spinnaker/core';

interface IUseManifestProps {
  manifest: IManifest;
}

export function useManifest({ manifest }: IUseManifestProps) {
  const ownerReferences = (): any[] => {
    const manifestRaw = manifest.manifest;
    if (
      manifestRaw != null &&
      manifestRaw.hasOwnProperty('metadata') &&
      manifestRaw.metadata.hasOwnProperty('ownerReferences') &&
      Array.isArray(manifestRaw.metadata.ownerReferences)
    ) {
      return manifestRaw.metadata.ownerReferences;
    } else {
      return [] as any[];
    }
  };

  const ownerIsController = (ownerReference: any): boolean => {
    return ownerReference.hasOwnProperty('controller') && ownerReference.controller === true;
  };

  const lowerCaseFirstLetter = (s: string): string => {
    return s.charAt(0).toLowerCase() + s.slice(1);
  };

  const manifestController = (): string | null => {
    const controller = ownerReferences().find(ownerIsController);
    if (typeof controller === 'undefined') {
      return null;
    } else {
      return lowerCaseFirstLetter(controller.kind) + ' ' + controller.name;
    }
  };

  return {
    manifestController: manifestController(),
  };
}
