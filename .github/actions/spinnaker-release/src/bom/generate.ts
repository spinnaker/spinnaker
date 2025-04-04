import * as core from '@actions/core';
import { Bom } from './bom';
import { services } from './services/all';
import * as util from '../util';
import { fromCurrent, VersionsDotYml } from './versionsDotYml';
import { forVersion, Changelog } from './changelog';

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

  core.info(`Generated BoM: \n${bom.toString()}`);
  core.info(`Generated versions.yml: \n${versionsYml.toString()}`);
  await publish(bom, versionsYml, changelog);
}

async function generateBom(): Promise<Bom> {
  core.info('Running BoM generator');

  const version = util.getInput('version');
  const bom = new Bom(version);
  for (const service of services) {
    bom.setService(service);
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
  return versionsYml;
}

async function publish(
  bom: Bom,
  versionDotYml: VersionsDotYml,
  changelog: Changelog,
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
}
