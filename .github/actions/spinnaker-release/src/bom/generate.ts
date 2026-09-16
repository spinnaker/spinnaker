import * as core from '@actions/core';
import * as fs from 'fs';
import { Bom } from './bom';
import { services } from './services/all';
import * as util from '../util';
import { fromCurrent, VersionsDotYml } from './versionsDotYml';
import { forVersion, Changelog } from './changelog';
import { ApiDocs, gateSource } from './apiDocs';
import { Version } from '../versions';

export async function generate(): Promise<void> {
  const bom = await generateBom().catch((err) => {
    core.error('Failed to generate BoM');
    throw err;
  });

  core.setOutput('bom', bom.toString());
  core.setOutput('bom-url', bom.getBucketFilePath());

  const versionsYml = await generateVersionsYml().catch((err) => {
    core.error('Failed to generate versions.yml');
    throw err;
  });

  core.setOutput('versions-yml', versionsYml.toString());
  core.setOutput('versions-yml-url', versionsYml.getBucketFilePath());

  const changelog = await generateChangelog().catch((err) => {
    core.error('Failed to generate changelog');
    throw err;
  });

  core.setOutput('changelog', changelog.markdown);
  core.setOutput('changelog-url', changelog.prUrl);

  const apiDocs = await generateApiDocs().catch((err) => {
    core.error('Failed to generate API docs');
    throw err;
  });

  core.setOutput('api-docs-url', apiDocs?.prUrl ?? '');

  core.info(`Generated BoM: \n${bom.toString()}`);
  core.info(`Generated versions.yml: \n${versionsYml.toString()}`);
  await publish(bom, versionsYml, changelog, apiDocs);
}

async function generateBom(): Promise<Bom> {
  core.info('Running BoM generator');

  const version = Version.parse(util.getInput('version'));
  if (!version) {
    throw new Error('Valid version must be provided to generate a BoM');
  }
  const bom = new Bom(version);
  for (const service of services) {
    bom.setService(service, version);
  }

  return bom;
}

async function generateChangelog(): Promise<Changelog> {
  core.info('Generating changelog');

  const version = util.getInput('version');
  const previousVersion = util.getInput('previous-version');
  return forVersion(version, previousVersion);
}

async function generateVersionsYml(): Promise<VersionsDotYml> {
  core.info('Running versions.yml generator');
  const versionsYml = await fromCurrent();
  if (util.getInput('add-to-versions-yml') == 'true') {
    versionsYml.addVersion(util.getInput('version'));
  }
  versionsYml.collapseVersions();
  return versionsYml;
}

async function generateApiDocs(): Promise<ApiDocs | null> {
  const version = util.getInput('version');
  const sources = [gateSource()].filter((source) => {
    if (!source.specPath) {
      core.info(
        `No spec path provided for ${source.service} - skipping API docs`,
      );
      return false;
    }
    if (!fs.existsSync(source.specPath)) {
      core.warning(
        `Spec path ${source.specPath} for ${source.service} does not exist - skipping API docs`,
      );
      return false;
    }
    return true;
  });

  if (sources.length === 0) {
    return null;
  }

  core.info('Running API docs generator');
  return new ApiDocs(version, sources);
}

async function publish(
  bom: Bom,
  versionDotYml: VersionsDotYml,
  changelog: Changelog,
  apiDocs: ApiDocs | null,
) {
  const publishBom = util.getInput('publish-bom');
  if (publishBom == 'true') {
    await bom.publish();
  } else {
    core.info('Not publishing BoM - publish-bom is false');
  }

  const addToVersionsYml = util.getInput('add-to-versions-yml');
  if (addToVersionsYml == 'true') {
    await versionDotYml.publish();
  } else {
    core.info('Not publishing versions.yml - add-to-versions-yml is false');
  }

  const publishChangelog = util.getInput('publish-changelog');
  if (publishChangelog == 'true') {
    await changelog.publish();
  } else {
    core.info('Not publishing changelog - publish-changelog is false');
  }

  const publishApiDocs = util.getInput('publish-api-docs');
  if (publishApiDocs == 'true' && apiDocs) {
    await apiDocs.publish();
  } else {
    core.info(
      'Not publishing API docs - publish-api-docs is false or no specs were found',
    );
  }
}
