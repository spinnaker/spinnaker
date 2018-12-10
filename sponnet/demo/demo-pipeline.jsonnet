local sponnet = import '../pipeline.libsonnet';
local deployment = import 'deployment.json';
local kubeutils = import 'kubeutils.libsonnet';

local canaryDeployment = kubeutils.canary(deployment);
local account = 'staging-demo';
local app = 'myapp';
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


local dockerArtifact = sponnet.artifacts
                       .dockerImage()
                       .withName(myDockerArtifactName)
                       .withReference(myDockerRegistry + '/' + myDockerRepository);

local expectedDocker = sponnet.expectedArtifact(myDockerArtifactName)
                       .withDefaultArtifact(dockerArtifact)
                       .withMatchArtifact(dockerArtifact)
                       .withUseDefaultArtifact(true)
                       .withUsePriorArtifact(false);

local gitlabArtifact = sponnet.artifacts
                       .gitlabFile()
                       .withLocation(myManifestArtifactLocation)
                       .withName(myManifestArtifactName)
                       .withReference(myManifestArtifactReference)
                       .withVersion(myManifestArtifactVersion);

local expectedManifest = sponnet.expectedArtifact(myManifestArtifactName)
                         .withDefaultArtifact(gitlabArtifact)
                         .withMatchArtifact(gitlabArtifact)
                         .withUsePriorArtifact(false)
                         .withUseDefaultArtifact(true);

local dockerTrigger = sponnet.triggers
                      .docker('myDockerTrigger')
                      // If ExpectedArtifact Required, add below.
                      // .withExpectedArtifacts([expectedDocker])
                      .withAccount(myDockerAccount)
                      .withOrganization(myDockerOrganization)
                      .withRegistry(myDockerRegistry)
                      .withRepository(myDockerRepository)
                      .withTag(myDockerTag);

local gitTrigger = sponnet.triggers
                   .git('myGitTrigger')
                   // If ExpectedArtifact Required, add below.
                   // .withExpectedArtifacts([expectedManifest])
                   .withBranch(myBranch)
                   .withProject(myProject)
                   .withSlug(mySlug)
                   .withSource(mySource);

local emailPipelineNotification = sponnet.notification
                                  .withAddress('someone@example.com')
                                  .withCC('test@example.com')
                                  .withLevel('pipeline')
                                  .withType('email')
                                  .withWhen('pipeline.starting');

local slackStageNotification = sponnet.notification
                               .withAddress(notificationAddress)
                               .withLevel('stage')
                               .withType(notificationType)
                               .withWhen('stage.starting')
                               .withWhen('stage.failed', 'testf: one two three, $variable, %value, "quoted": https://example.com')
                               .withWhen('stage.complete', 'test');

local manualJudgment = sponnet.stages
                       .manualJudgment('Manual Judgment')
                       .withInstructions('Do you want to go ahead?')
                       .withJudgmentInputs(['yes', 'no']);

local checkPreconditions = sponnet.stages
                           .checkPreconditions('Confirm Judgment')
                           .withExpression("${ #judgment('Manual Judgment') == 'yes' }", true)
                           .withRequisiteStages(manualJudgment);

local wait = sponnet.stages
             .wait('Wait')
             .withSkipWaitText('Custom wait message')
             .withWaitTime(30)
             .withRequisiteStages(checkPreconditions);


local whitelist = {
  endHour: 7,
  endMin: 0,
  startHour: 5,
  startMin: 0,
};

local deployManifestArtifact = sponnet.stages
                               .deployManifest('Deploy a manifest with artifact')
                               .withAccount(account)
                               .withManifestArtifact(expectedManifest)
                               .withManifestArtifactAccount(myManifestArtifactAccount)
                               .withMoniker(moniker)
                               .withOverrideTimeout('300000')
                               .withRestrictedExecutionWindow(['1', '2', '3'], whitelist)
                               .withRequisiteStages(wait);

local deployManifestTextBaseline = sponnet.stages
                                   .deployManifest('Deploy a manifest')
                                   .withAccount(account)
                                   .withManifests(deployment)
                                   .withMoniker(moniker)
                                   .withRequisiteStages(wait);

local deployManifestTextCanary = sponnet.stages
                                 .deployManifest('Deploy a canary manifest')
                                 .withManifests(canaryDeployment)
                                 .withAccount(account)
                                 .withMoniker(moniker)
                                 .withRequisiteStages(wait);

local findArtifactsFromResource = sponnet.stages
                                  .findArtifactsFromResource('Find nginx-deployment')
                                  .withAccount(account)
                                  .withLocation('default')
                                  .withManifestName('Deployment nginx-deployment')
                                  .withRequisiteStages([deployManifestTextBaseline, deployManifestTextCanary, deployManifestArtifact]);

local jenkinsJob = sponnet.stages
                   .jenkins('Run Jenkins Job')
                   .withJob(myJenkinsJob)
                   .withMarkUnstableAsSuccessful('false')
                   .withMaster(myJenkinsMaster)
                   .withNotifications(slackStageNotification)
                   .withOverrideTimeout('300000')
                   .withRequisiteStages(findArtifactsFromResource)
                   .withWaitForCompletion('true');

sponnet.pipeline()
.withApplication(app)
.withExpectedArtifacts([expectedDocker, expectedManifest])
.withId('sponnet-demo-pipeline')
.withName('Demo pipeline')
.withNotifications([emailPipelineNotification])
.withTriggers([dockerTrigger, gitTrigger])
.withStages([manualJudgment, checkPreconditions, wait, deployManifestTextBaseline, deployManifestTextCanary, deployManifestArtifact, findArtifactsFromResource, jenkinsJob])
