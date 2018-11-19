{
  pipeline():: {
    keepWaitingPipelines: false,
    limitConcurrent: true,
    notifications: [],
    stages: [],
    triggers: [],
    withApplication(application):: self + { application: application },
    withExpectedArtifacts(expectedArtifacts):: self + if std.type(expectedArtifacts) == 'array' then { expectedArtifacts: expectedArtifacts } else { expectedArtifacts: [expectedArtifacts] },
    withId(id):: self + { id: id },
    withKeepWaitingPipelines(keepWaitingPipelines):: self + { keepWaitingPipelines: keepWaitingPipelines },
    withLimitConcurrent(limitConcurrent):: self + { limitConcurrent: limitConcurrent },
    withName(name):: self + { name: name },
    withNotifications(notifications):: self + if std.type(notifications) == 'array' then { notifications: notifications } else { notifications: [notifications] },
    withStages(stages):: self + if std.type(stages) == 'array' then { stages: stages } else { stages: [stages] },
    withTriggers(triggers):: self + if std.type(triggers) == 'array' then { triggers: triggers } else { triggers: [triggers] },
  },

  moniker(app, cluster):: {
    app: app,
    cluster: cluster,
    withStack(stack):: self + { stack: stack },
    withDetail(detail):: self + { detail: detail },
  },

  // artifacts

  artifact(type):: {
    type: type,
    withArtifactAccount(artifactAccount):: self + { artifactAccount: artifactAccount },
    withLocation(location):: self + { location: location },
    withName(name):: self + { name: name },
    withReference(reference):: self + { reference: reference },
    withVersion(version):: self + { version: version },
  },

  local artifact = self.artifact,
  artifacts:: {
    bitbucketFile():: artifact('bitbucket/file'),
    dockerImage():: artifact('docker/image'),
    embeddedBase64():: artifact('embedded/base64'),
    gcsObject():: artifact('gcs/object'),
    githubFile():: artifact('github/file'),
    gitlabFile():: artifact('gitlab/file'),
    httpFile():: artifact('http/file'),
    // kubernetesObject to be tested. Where kind is Deployment/Configmap/Service/etc
    kubernetesObject(kind):: artifact('kubernetes/' + kind),
  },

  // expected artifacts
  // TODO: This section may need splitting out by artifact type due to differing field requirements.

  expectedArtifact(id):: {
    id: id,
    withMatchArtifact(matchArtifact):: self + {
      matchArtifact+: {
        // TODO: For Docker, the name field should be registry and repository.
        name: matchArtifact.name,
        type: matchArtifact.type,
      },
    },
    withDefaultArtifact(defaultArtifact):: self + {
      defaultArtifact: {
        reference: defaultArtifact.reference,
        type: defaultArtifact.type,
        // TODO: Some Artifact types (docker) don't require version to be set. It may be better to do this differently.
        [if std.objectHas(defaultArtifact, 'version') then 'version']: defaultArtifact.version,
      },
    },
    withUsePriorArtifact(usePriorArtifact):: self + { usePriorArtifact: usePriorArtifact },
    withUseDefaultArtifact(useDefaultArtifact):: self + { useDefaultArtifact: useDefaultArtifact },
  },

  // notifications

  notification:: {
    withAddress(address):: self + { address: address },
    withCC(cc):: self + { cc: cc },
    withLevel(level):: self + { level: level },
    // Custom notification messages are optional
    withWhen(when, message=false):: self + {
      when+: [when],
      [if std.isString(message) then 'message']+: {
        [when]: {
          text: message,
        },
      },
    },
    withType(type):: self + { type: type },
  },
  local notification = self.notification,

  // triggers

  trigger(name, type):: {
    enabled: true,
    name: name,
    type: type,
    withExpectedArtifacts(expectedArtifacts):: self + if std.type(expectedArtifacts) == 'array' then { expectedArtifactIds: std.map(function(expectedArtifact) expectedArtifact.id, expectedArtifacts) } else { expectedArtifactIds: [expectedArtifacts.id] },
  },

  local trigger = self.trigger,
  triggers:: {
    docker(name):: trigger(name, 'docker') {
      withAccount(account):: self + { account: account },
      withExpectedArtifacts(expectedArtifacts):: self + if std.type(expectedArtifacts) == 'array' then { expectedArtifactIds: std.map(function(expectedArtifact) expectedArtifact.id, expectedArtifacts) } else { expectedArtifactIds: [expectedArtifacts.id] },
      withOrganization(organization):: self + { organization: organization },
      withRegistry(registry):: self + { registry: registry },
      withRepository(repository):: self + { repository: repository },
      withTag(tag):: self + { tag: tag },
    },
    git(name):: trigger(name, 'git') {
      withBranch(branch):: self + { branch: branch },
      withProject(project):: self + { project: project },
      withSlug(slug):: self + { slug: slug },
      withSource(source):: self + { source: source },
    },

  },

  // stages

  stage(name, type):: {
    refId: name,
    name: name,
    type: type,
    requisiteStageRefIds: [],
    withNotifications(notifications):: self + { sendNotifications: true } + if std.type(notifications) == 'array' then { notifications: notifications } else { notifications: [notifications] },
    withRequisiteStages(stages):: self + if std.type(stages) == 'array' then { requisiteStageRefIds: std.map(function(stage) stage.refId, stages) } else { requisiteStageRefIds: [stages.refId] },
    // execution options
    // TODO (kskewes): Use a toggle or other mechanism to enforce single choice of `If stage fails`
    withCompleteOtherBranchesThenFail(completeOtherBranchesThenFail):: self + { completeOtherBranchesThenFail: completeOtherBranchesThenFail },
    withContinuePipeline(continuePipeline):: self + { continuePipeline: continuePipeline },
    withFailPipeline(failPipeline):: self + { failPipeline: failPipeline },
    withFailOnFailedExpressions(failOnFailedExpressions):: self + { failOnFailedExpressions: failOnFailedExpressions },
    withStageEnabled(expression):: self + { stageEnabled: { type: 'expression', expression: expression } },
    withRestrictedExecutionWindow(days, whitelist, jitter=null):: self + { restrictExecutionDuringTimeWindow: true } +
                                                                  (if std.type(days) == 'array' then { restrictedExecutionWindow+: { days: days } } else { restrictedExecutionWindow+: { days: [days] } }) +
                                                                  (if std.type(whitelist) == 'array' then { restrictedExecutionWindow+: { whitelist: whitelist } } else { restrictedExecutionWindow+: { whitelist: [whitelist] } }) +
                                                                  (std.prune({ restrictedExecutionWindow+: { jitter: jitter } })),
    withSkipWindowText(skipWindowText):: self + { skipWindowText: skipWindowText },
    withOverrideTimeout(timeoutMs):: self + { overrideTimeout: true, stageTimeoutMs: timeoutMs },
  },

  local stage = self.stage,
  stages:: {
    wait(name):: stage(name, 'wait') {
      withWaitTime(waitTime):: self + { waitTime: waitTime },
      withSkipWaitText(skipWaitText):: self + { skipWaitText: skipWaitText },
    },

    // agnostic stages

    checkPreconditions(name):: stage(name, 'checkPreconditions') {
      preconditions: [],
      withExpression(expression, failPipeline):: self + {
        preconditions+: [{
          context: { expression: expression },
          failPipeline: failPipeline,
          type: 'expression',
        }],
      },
      withClusterSize(cluster, comparison, credentials, expected, regions, failPipeline):: self + {
        preconditions+: [{
          context: {
            cluster: cluster,
            comparison: comparison,
            credentials: credentials,
            expected: expected,
            regions: if std.type(regions) == 'array' then { regions: regions } else { regions: [regions] },
          },
          failPipeline: failPipeline,
          type: 'clusterSize',
        }],
      },
    },

    findArtifactFromExecution(name):: stage(name, 'findArtifactFromExecution') {
      withApplication(application):: self + { application: application },
      withExecutionOptions(executionOptions):: self + if std.type(executionOptions) == 'array' then { executionOptions: executionOptions } else { executionOptions: [executionOptions] },
      withExpectedArtifact(expectedArtifact):: self + {
        expectedArtifact: {
          id: expectedArtifact.id,
          matchArtifact: expectedArtifact.matchArtifact,
        },
      },
      withPipeline(pipeline):: self + { pipeline: pipeline },
    },

    manualJudgment(name):: stage(name, 'manualJudgment') {
      judgmentInputs: [],
      withInstructions(instructions):: self + { instructions: instructions },
      withJudgmentInputs(judgmentInputs):: self + if std.type(judgmentInputs) == 'array' then { judgmentInputs: std.map(function(input) { value: input }, judgmentInputs) } else { judgmentInputs: [{ value: judgmentInputs }] },
      withNotifications(notifications):: self + { sendNotifications: true } + if std.type(notifications) == 'array' then { notifications: notifications } else { notifications: [notifications] },
      withPropagateAuthenticationContext(propagateAuthenticationContext):: self + { propagateAuthenticationContext: propagateAuthenticationContext },
      withSendNotifications(sendNotifications):: self + { sendNotifications: sendNotifications },
    },

    // jenkins stages

    jenkins(name):: stage(name, 'jenkins') {
      withJob(job):: self + { job: job },
      withMaster(master):: self + { master: master },
      withPropertyFile(propertyFile):: self + { propertyFile: propertyFile },
      withMarkUnstableAsSuccessful(markUnstableAsSuccessful):: self + { markUnstableAsSuccessful: markUnstableAsSuccessful },
      withWaitForCompletion(waitForCompletion):: self + { waitForCompletion: waitForCompletion },
    },

    // kubernetes stages

    deployManifest(name):: stage(name, 'deployManifest') {
      cloudProvider: 'kubernetes',
      source: 'text',
      withAccount(account):: self + { account: account },
      withManifestArtifactAccount(account):: self + { manifestArtifactAccount: account },
      // Add/Default to embedded-artifact? If so add:  /* , manifestArtifactAccount: 'embedded-artifact' */
      withManifestArtifact(artifact):: self + { manifestArtifactId: artifact.id, source: 'artifact' },
      withManifests(manifests):: self + if std.type(manifests) == 'array' then { manifests: manifests } else { manifests: [manifests] },
      withMoniker(moniker):: self + { moniker: moniker },
    },
    deleteManifest(name):: stage(name, 'deleteManifest') {
      cloudProvider: 'kubernetes',
      options: {
        cascading: true,
      },
      withAccount(account):: self + { account: account },
      withKinds(kinds):: self + if std.type(kinds) == 'array' then { kinds: kinds } else { kinds: [kinds] },
      withNamespace(namespace):: self + { location: namespace },
      withLabelSelectors(selectors):: self + if std.type(selectors) == 'array' then { labelSelectors: { selectors: selectors } } else { labelSelectors: { selectors: [selectors] } },
      withGracePeriodSeconds(seconds):: self.options { gracePeriodSeconds: seconds },
      withManifestName(kind, name):: self.options { manifestName: kind + ' ' + name },
    },
    findArtifactsFromResource(name):: stage(name, 'findArtifactsFromResource') {
      cloudProvider: 'kubernetes',
      withAccount(account):: self + { account: account },
      withExpectedArtifacts(expectedArtifacts):: self + if std.type(expectedArtifacts) == 'array' then { expectedArtifacts: expectedArtifacts } else { expectedArtifacts: [expectedArtifacts] },
      withLocation(location):: self + { location: location },
      withManifestName(manifestName):: self + { manifestName: manifestName },
    },
    patchManifest(name):: stage(name, 'patchManifest') {
      cloudProvider: 'kubernetes',
      source: 'text',
      options: {
        mergeStrategy: 'strategic',
        record: true,
      },
      withAccount(account):: self + { account: account },
      withNamespace(namespace):: self + { location: namespace },
      withPatchBody(patchBody): self + { patchBody: patchBody },
      withManifestName(kind, name):: self.options { manifestName: kind + ' ' + name },
    },
    scaleManifest(name): stage(name, 'scaleManifest') {
      cloudProvider: 'kubernetes',
      withAccount(account):: self + { account: account },
      withNamespace(namespace):: self + { location: namespace },
      withReplicas(replicas): self + { replicas: replicas },
      withManifestName(kind, name):: self.options { manifestName: kind + ' ' + name },
    },
    undoRolloutManifest(name): stage(name, 'undoRolloutManifest') {
      cloudProvider: 'kubernetes',
      withAccount(account):: self + { account: account },
      withNamespace(namespace):: self + { location: namespace },
      withRevisionsBack(revisionsBack): self + { numRevisionsBack: revisionsBack },
      withManifestName(kind, name):: self.options { manifestName: kind + ' ' + name },
    },

    // pipeline stages

    pipeline(name):: stage(name, 'pipeline') {
      withApplication(application):: self + { application: application },
      withPipeline(pipeline):: self + { pipeline: self.application + '-' + pipeline },
      withWaitForCompletion(waitForCompletion):: self + { waitForCompletion: waitForCompletion },
    },

    // wercker stages
    // This stage has only been written from spec and not tested
    wercker(name):: stage(name, 'wercker') {
      withJob(job):: self + { job: job },
      withMaster(master):: self + { master: master },
    },

  },

  // kubernetes-provider help

  kubernetes:: {
    selector(kind):: {
      kind: kind,
    },
    local selector = self.selector,
    anySelector(key, value):: selector('ANY'),
    equalsSelector(key, value):: selector('EQUALS') {
      key: key,
      values: [value],
    },
    notEqualsSelector(key, value):: selector('NOT_EQUALS') {
      key: key,
      values: [value],
    },
    containsSelector(key, values):: selector('CONTAINS') {
      key: key,
      values: values,
    },
    notContainsSelector(key, values):: selector('NOT_CONTAINS') {
      key: key,
      values: values,
    },
    existsSelector(key):: selector('EXISTS') {
      key: key,
    },
    notExistsSelector(key):: selector('NOT_EXISTS') {
      key: key,
    },
  },
}
