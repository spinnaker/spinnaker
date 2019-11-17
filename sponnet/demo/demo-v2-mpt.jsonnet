local pipelines = import '../pipeline.libsonnet';
local mpt = import '../v2PipelineTemplate.libsonnet';
local deployment = import 'deployment.json';
local kubeutils = import 'kubeutils.libsonnet';

local canaryDeployment = kubeutils.canary(deployment);
local account = 'staging-demo';
local app = 'myapp';
local moniker = pipelines.moniker(app, 'some-cluster')
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


local dockerArtifact = pipelines.artifacts
                       .dockerImage()
                       .withName(myDockerArtifactName)
                       .withReference(myDockerRegistry + '/' + myDockerRepository);

local expectedDocker = pipelines.expectedArtifact(myDockerArtifactName)
                       .withDefaultArtifact(dockerArtifact)
                       .withMatchArtifact(dockerArtifact)
                       .withUseDefaultArtifact(true)
                       .withUsePriorArtifact(false);

local gitlabArtifact = pipelines.artifacts
                       .gitlabFile()
                       .withLocation(myManifestArtifactLocation)
                       .withName(myManifestArtifactName)
                       .withReference(myManifestArtifactReference)
                       .withVersion(myManifestArtifactVersion);

local expectedManifest = pipelines.expectedArtifact(myManifestArtifactName)
                         .withDefaultArtifact(gitlabArtifact)
                         .withMatchArtifact(gitlabArtifact)
                         .withUsePriorArtifact(false)
                         .withUseDefaultArtifact(true);

local dockerTrigger = pipelines.triggers
                      .docker('myDockerTrigger')
                      // If ExpectedArtifact Required, add below.
                      // .withExpectedArtifacts([expectedDocker])
                      .withAccount(myDockerAccount)
                      .withOrganization(myDockerOrganization)
                      .withRegistry(myDockerRegistry)
                      .withRepository(myDockerRepository)
                      .withTag(myDockerTag);

local gitTrigger = pipelines.triggers
                   .git('myGitTrigger')
                   // If ExpectedArtifact Required, add below.
                   // .withExpectedArtifacts([expectedManifest])
                   .withBranch(myBranch)
                   .withProject(myProject)
                   .withSlug(mySlug)
                   .withSource(mySource);

local emailPipelineNotification = pipelines.notification
                                  .withAddress('someone@example.com')
                                  .withCC('test@example.com')
                                  .withLevel('pipeline')
                                  .withType('email')
                                  .withWhen('pipeline.starting');

local slackStageNotification = pipelines.notification
                               .withAddress(notificationAddress)
                               .withLevel('stage')
                               .withType(notificationType)
                               .withWhen('stage.starting')
                               .withWhen('stage.failed', 'testf: one two three, $variable, %value, "quoted": https://example.com')
                               .withWhen('stage.complete', 'test');

local manualJudgment = pipelines.stages
                       .manualJudgment('Manual Judgment')
                       .withInstructions('Do you want to go ahead?')
                       .withJudgmentInputs(['yes', 'no']);

local checkPreconditions = pipelines.stages
                           .checkPreconditions('Confirm Judgment')
                           .withExpression("${ #judgment('Manual Judgment') == 'yes' }", true)
                           .withRequisiteStages(manualJudgment);

local wait = pipelines.stages
             .wait('Wait')
             .withSkipWaitText('Custom wait message')
             .withWaitTime('${ templateVariables.waitTime }')
             .withRequisiteStages(checkPreconditions);


local whitelist = {
  endHour: 7,
  endMin: 0,
  startHour: 5,
  startMin: 0,
};

local deployManifestArtifact = pipelines.stages
                               .deployManifest('Deploy a manifest with artifact')
                               .withAccount(account)
                               .withManifestArtifact(expectedManifest)
                               .withManifestArtifactAccount(myManifestArtifactAccount)
                               .withMoniker(moniker)
                               .withOverrideTimeout('300000')
                               .withRestrictedExecutionWindow(['1', '2', '3'], whitelist)
                               .withRequisiteStages(wait);

local deployManifestTextBaseline = pipelines.stages
                                   .deployManifest('Deploy a manifest')
                                   .withAccount(account)
                                   .withManifests(deployment)
                                   .withMoniker(moniker)
                                   .withRequisiteStages(wait);

local deployManifestTextCanary = pipelines.stages
                                 .deployManifest('Deploy a canary manifest')
                                 .withManifests(canaryDeployment)
                                 .withAccount(account)
                                 .withMoniker(moniker)
                                 .withRequisiteStages(wait);

local findArtifactsFromResource = pipelines.stages
                                  .findArtifactsFromResource('Find nginx-deployment')
                                  .withAccount(account)
                                  .withLocation('default')
                                  .withManifestName('Deployment','nginx-deployment')
                                  .withRequisiteStages([deployManifestTextBaseline, deployManifestTextCanary, deployManifestArtifact]);

local jenkinsJob = pipelines.stages
                   .jenkins('Run Jenkins Job')
                   .withJob(myJenkinsJob)
                   .withMarkUnstableAsSuccessful('false')
                   .withMaster(myJenkinsMaster)
                   .withNotifications(slackStageNotification)
                   .withOverrideTimeout('300000')
                   .withRequisiteStages(findArtifactsFromResource)
                   .withWaitForCompletion('true');

local pipeline = pipelines.pipeline()
.withApplication(app)
.withExpectedArtifacts([expectedDocker, expectedManifest])
.withName('Demo pipeline')
.withNotifications([emailPipelineNotification])
.withTriggers([dockerTrigger, gitTrigger])
.withStages([manualJudgment, checkPreconditions, wait, deployManifestTextBaseline, deployManifestTextCanary, deployManifestArtifact, findArtifactsFromResource, jenkinsJob]);

local metadata = mpt.metadata()
.withName('My New MPT')
.withDescription('Totally Rad k8s Pipeline')
.withOwner('jacobkiefer@google.com')
.withScopes(['global']);

local waitTime = mpt.variable()
.withType('int')
.withDefaultValue(42)
.withDescription('The length of the segment of time this pipeline shall wait')
.withName('waitTime');

mpt.pipelineTemplate()
.withId('my-new-mpt')
.withMetadata(metadata)
.withVariables([waitTime])
.withPipeline(pipeline)
