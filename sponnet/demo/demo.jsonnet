local deployment = import 'deployment.json';
local kubeutils = import 'kubeutils.libsonnet';
local sponnet = import 'pipeline.libsonnet';

local canary_deployment = kubeutils.canary(deployment);
local account = 'staging-demo';
local app = 'cluster';
local moniker = sponnet.moniker(app, 'some-cluster')
                .withStack('someStack')
                .withDetail('someDetail');

local myJenkinsMaster = 'staging-jenkins';
local myJenkinsJob = 'smoketest';

local notificationAddress = 'development';
local notificationType = 'slack';
local notificationConditions = ['pipeline.starting', 'pipeline.failed', 'pipeline.complete'];

local myManifestArtifactName = 'app/manifest.yaml';
local myManifestArtifactVersion = 'master';
local myManifestArtifactReference = 'https://gitlab.com/api/v4/projects/your-org%2Fyour-project/repository/files/app%2Fmanifest%2Eyaml/raw';
local myManifestArtifactLocation = 'someLocation';
// Must be specified in pipeline, but not in artifact creation
local myManifestArtifactAccount = 'gitlab-account';

local myDockerArtifactName = 'docker-name';
local myDockerAccount = 'docker-account';
local myDockerOrganization = 'your-docker-org';
local myDockerRegistry = 'index.docker.io';
local myDockerRepository = 'yourorg/app';
local myDockerTag = '^git-.*$';

local myBranch = 'master';
local myProject = 'your-org';
local mySlug = 'your-project';
local mySource = 'gitlab';


local docker_artifact = sponnet.artifacts
                        .dockerImage()
                        .withName(myDockerArtifactName)
                        .withReference(myDockerRegistry + '/' + myDockerRepository);

local expected_docker = sponnet.expectedArtifact(myDockerArtifactName)
                        .withMatchArtifact(docker_artifact)
                        .withDefaultArtifact(docker_artifact)
                        .withUsePriorArtifact(false)
                        .withUseDefaultArtifact(true);

local gitlab_artifact = sponnet.artifacts
                        .gitlabFile()
                        .withName(myManifestArtifactName)
                        .withVersion(myManifestArtifactVersion)
                        .withReference(myManifestArtifactReference)
                        .withLocation(myManifestArtifactLocation);

local expected_manifest = sponnet.expectedArtifact(myManifestArtifactName)
                          .withMatchArtifact(gitlab_artifact)
                          .withDefaultArtifact(gitlab_artifact)
                          .withUsePriorArtifact(false)
                          .withUseDefaultArtifact(true);

local docker_trigger = sponnet.triggers
                       .docker('myDockerTrigger')
                       // If ExpectedArtifact Required, add below.
                       // .withExpectedArtifacts([expected_docker])
                       .withAccount(myDockerAccount)
                       .withOrganization(myDockerOrganization)
                       .withRegistry(myDockerRegistry)
                       .withRepository(myDockerRepository)
                       .withTag(myDockerTag);

local git_trigger = sponnet.triggers
                    .git('myGitTrigger')
                    // If ExpectedArtifact Required, add below.
                    // .withExpectedArtifacts([expected_manifest])
                    .withBranch(myBranch)
                    .withProject(myProject)
                    .withSlug(mySlug)
                    .withSource(mySource);

local slack = sponnet.notifications
              .withAddress(notificationAddress)
              .withType(notificationType)
              .withWhen('starting')
              .withWhen('failed', 'testf: one two three, $variable, %value, "quoted": https://example.com')
              .withWhen('complete', 'test');

local wait = sponnet.stages
             .wait('Wait')
             .withWaitTime(30);

local manifest_artifact = sponnet.stages
                          .deploy_manifest('Deploy a manifest with artifact')
                          .withManifestArtifact(expected_manifest)
                          .withManifestArtifactAccount(myManifestArtifactAccount)
                          .withAccount(account)
                          .withMoniker(moniker)
                          .withOverrideTimeout('300000')
                          .withRequisiteStages(wait);

local manifest_baseline = sponnet.stages
                          .deploy_manifest('Deploy a manifest')
                          .withManifests(deployment)
                          .withAccount(account)
                          .withMoniker(moniker)
                          .withRequisiteStages(wait);

local manifest_canary = sponnet.stages
                        .deploy_manifest('Deploy a canary manifest')
                        .withManifests(canary_deployment)
                        .withAccount(account)
                        .withMoniker(moniker)
                        .withRequisiteStages(wait);

local jenkins_job = sponnet.stages
                    .jenkins('Run Jenkins Job')
                    .withMaster(myJenkinsMaster)
                    .withJob(myJenkinsJob)
                    .withOverrideTimeout('300000')
                    .withRequisiteStages(wait);

sponnet.pipeline()
.withApplication(app)
.withExpectedArtifacts([expected_docker, expected_manifest])
.withName('Demo pipeline')
.withNotifications(slack)
.withTriggers([docker_trigger, git_trigger])
.withStages([wait, manifest_baseline, manifest_canary, manifest_artifact, jenkins_job])
