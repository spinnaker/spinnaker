import { Service } from './service';
import { StoredYml } from '../gcp/stored_yml';
import * as util from '../util';
import * as Path from 'path';

export class Bom extends StoredYml {
  artifactSources: Map<string, string>;
  dependencies: Map<string, Map<string, string>>;
  services: Map<string, Map<string, string>>;
  timestamp: string;
  version: string;

  constructor(version: string) {
    super();
    this.artifactSources = this.getDefaultArtifactSources();
    this.dependencies = this.getDefaultDependencies();
    this.services = new Map<string, Map<string, string>>();
    this.timestamp = new Date().toISOString();
    this.version = version;
  }

  getDefaultArtifactSources(): Map<string, string> {
    return new Map(
      Object.entries({
        debianRepository: util.getInput('as-debian-repository'),
        dockerRegistry: util.getInput('as-docker-registry'),
        gitPrefix: util.getInput('as-git-prefix'),
        googleImageProject: util.getInput('as-google-image-project'),
      }),
    );
  }

  getDefaultDependencies(): Map<string, Map<string, string>> {
    return new Map(
      Object.entries({
        consul: new Map(
          Object.entries({
            version: util.getInput('dep-consul-version'),
          }),
        ),
        redis: new Map(
          Object.entries({
            version: util.getInput('dep-redis-version'),
          }),
        ),
        vault: new Map(
          Object.entries({
            version: util.getInput('dep-vault-version'),
          }),
        ),
      }),
    );
  }

  setArtifactSources(sources: Map<string, string>) {
    this.artifactSources = sources;
  }

  setDependency(dep: string, version: string) {
    this.dependencies.set(
      dep,
      new Map(
        Object.entries({
          version: version,
        }),
      ),
    );
  }

  setService(service: Service) {
    this.services.set(
      service.name,
      new Map(
        Object.entries({
          commit: service.getCommit(),
          version: service.getVersion(),
        }),
      ),
    );
  }

  setTimestamp(timestamp: string) {
    this.timestamp = timestamp;
  }

  async getAtVersion(version: string): Promise<string | null> {
    const filename = `${version}.yml`;
    return this.get(
      this.getBucket(),
      Path.join(util.getInput('bom-bucket-path'), filename),
    );
  }

  override async publish(): Promise<void> {
    // Check if this BoM already exists, unless we are allowing overwrites
    if (util.getInput('allow-bom-overwrite') !== 'true') {
      const existing = await this.getCurrent();
      if (existing) {
        throw new Error(
          `Cannot create BoM ${this.version} - already exists, and option allow-bom-overwrite is not set.`,
        );
      }
    }

    return super.publish();
  }

  override getBucket(): string {
    return util.getInput('bucket');
  }

  override getBucketFilePath(): string {
    return Path.join(util.getInput('bom-bucket-path'), this.getFilename());
  }

  override getFilename(): string {
    return `${this.version}.yml`;
  }
}
