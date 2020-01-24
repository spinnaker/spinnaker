import { Application, IManifest } from 'core';
import { ManifestReader } from './ManifestReader';

export interface IManifestContainer {
  manifest: IManifest;
}

export interface IStageManifest {
  kind: string;
  apiVersion: string;
  metadata: {
    namespace: string;
    name: string;
  };
}

export interface IManifestParams {
  account: string;
  location: string;
  name: string;
}

export type IManifestCallback = (manifest: IManifest) => void;

export class ManifestService {
  public static makeManifestRefresher(
    app: Application,
    params: IManifestParams,
    container: IManifestContainer,
  ): () => void {
    const onUpdate = (manifest: IManifest) => {
      container.manifest = manifest || container.manifest;
    };
    return ManifestService.subscribe(app, params, onUpdate);
  }

  public static subscribe(app: Application, params: IManifestParams, fn: IManifestCallback): () => void {
    ManifestService.updateManifest(params, fn);
    return app.onRefresh(null, () => ManifestService.updateManifest(params, fn));
  }

  private static updateManifest(params: IManifestParams, fn: IManifestCallback) {
    ManifestReader.getManifest(params.account, params.location, params.name).then(manifest => fn(manifest));
  }

  public static manifestIdentifier(manifest: IStageManifest) {
    const kind = manifest.kind.toLowerCase();
    // manifest.metadata.namespace doesn't exist if it's a namespace being deployed
    const namespace = (manifest.metadata.namespace || '_').toLowerCase();
    const name = manifest.metadata.name.toLowerCase();
    const apiVersion = (manifest.apiVersion || '_').toLowerCase();
    // assuming this identifier is opaque and not parsed anywhere. Including the
    // apiVersion will prevent collisions with CRD kinds without having any visible
    // effect elsewhere
    return `${namespace} ${kind} ${apiVersion} ${name}`;
  }
}
