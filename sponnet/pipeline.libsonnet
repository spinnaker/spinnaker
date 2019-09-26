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
    withRoles(roles):: self + if std.type(roles) == 'array' then { roles: roles } else { roles: [roles] },
    withStages(stages):: self + if std.type(stages) == 'array' then { stages: stages } else { stages: [stages] },
    withTriggers(triggers):: self + if std.type(triggers) == 'array' then { triggers: triggers } else { triggers: [triggers] },
    withParameters(parameters):: self + if std.type(parameters) == 'array' then { parameterConfig: parameters } else { parameterConfig: [parameters] },
    withLock(description='', allowUnlockUi=true):: self + {
      locked: { allowUnlockUi: allowUnlockUi, description: description, ui: true }
    },

    // v2 MPT fields
    withTemplate(templateArtifact):: self + { template: templateArtifact },
    withSchema(schema):: self + { schema: schema },
    withInherit(inheritedFields):: self + if std.type(inheritedFields) == 'array' then { inherit: inheritedFields } else { inherit: [inheritedFields] },
    withVariableValues(variables):: self + { variables: variables },  // variables are key-value pairs of <variable name> -> <variable value>
  },

  moniker(app, cluster=null):: {
    app: app,
    [if cluster != null then "cluster"]: cluster,
    withStack(stack):: self + { stack: stack },
    withDetail(detail):: self + { detail: detail },
  },

  // artifacts

  artifact(type, kind):: {
    type: type,
    kind: kind,
    withArtifactAccount(artifactAccount):: self + { artifactAccount: artifactAccount },
    withLocation(location):: self + { location: location },
    withName(name):: self + { name: name },
    withReference(reference):: self + { reference: reference },
    withVersion(version):: self + { version: version },
    withKind(kind):: self + { kind: kind },
  },

  local artifact = self.artifact,
  artifacts:: {
    bitbucketFile():: artifact('bitbucket/file', 'bitbucket'),
    dockerImage():: artifact('docker/image', 'docker'),
    embeddedBase64():: artifact('embedded/base64', 'base64'),
    gcsObject():: artifact('gcs/object', 'gcs'),
    githubFile():: artifact('github/file', 'github'),
    gitlabFile():: artifact('gitlab/file', 'gitlab'),
    httpFile():: artifact('http/file', 'http'),
    s3Object():: artifact('s3/object', 's3'),
    // kubernetesObject to be tested. Where kind is Deployment/Configmap/Service/etc
    kubernetesObject(kind):: artifact('kubernetes/' + kind, 'custom'),
    front50PipelineTemplate():: artifact('front50/pipelineTemplate', '').withArtifactAccount('front50ArtifactCredentials'),  // credentials are static
  },

  // expected artifacts
  // TODO: This section may need splitting out by artifact type due to differing field requirements.

  expectedArtifact(id):: {
    id: id,
    displayName: id,
    withMatchArtifact(matchArtifact):: self + {
      matchArtifact+: {
        // TODO: For Docker, the name field should be registry and repository.
        name: matchArtifact.name,
        type: matchArtifact.type,
        kind: matchArtifact.kind,
        [if 'artifactAccount' in matchArtifact then 'artifactAccount']: matchArtifact.artifactAccount,
      },
    },
    withDefaultArtifact(defaultArtifact):: self + {
      defaultArtifact: {
        reference: defaultArtifact.reference,
        type: defaultArtifact.type,
        kind: if defaultArtifact.kind == 'custom' then defaultArtifact else 'default.' + defaultArtifact.kind,
        // TODO: Some Artifact types (docker) don't require version to be set. It may be better to do this differently.
        [if 'version' in defaultArtifact then 'version']: defaultArtifact.version,
        [if 'name' in defaultArtifact then 'name']: defaultArtifact.name,
        [if 'artifactAccount' in defaultArtifact then 'artifactAccount']: defaultArtifact.artifactAccount,
      },
    },
    withDisplayName(displayName):: self + { displayName: displayName },
    withUsePriorArtifact(usePriorArtifact):: self + { usePriorArtifact: usePriorArtifact },
    withUseDefaultArtifact(useDefaultArtifact):: self + { useDefaultArtifact: useDefaultArtifact },
  },

  inputArtifact(id):: {
    id: id,
    fromAccount(account):: self + {
      account: account
    },
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

  // parameters
  parameter(name):: {
    name: name,
    required: false,
    hasOptions: false,
    withLabel(label):: self + { label: label },
    withDescription(description) :: self + { description: description },
    withDefaultValue(default) :: self + { default: default },
    isRequired(isRequired):: self + { required: isRequired },
    withOptions(options) :: self + { hasOptions: true, options: [{ value: x } for x in options]},
  },

  // triggers

  trigger(name, type):: {
    enabled: true,
    name: name,
    type: type,
    withExpectedArtifacts(expectedArtifacts):: self + if std.type(expectedArtifacts) == 'array' then { expectedArtifactIds: std.map(function(expectedArtifact) expectedArtifact.id, expectedArtifacts) } else { expectedArtifactIds: [expectedArtifacts.id] },
    isEnabled(isEnabled):: self + { enabled: isEnabled },
  },

  local trigger = self.trigger,
  triggers:: {
    docker(name):: trigger(name, 'docker') {
      withAccount(account):: self + { account: account },
      withExpectedArtifacts(expectedArtifacts):: self + if std.type(expectedArtifacts) == 'array' then { expectedArtifactIds: std.map(function(expectedArtifact) expectedArtifact.id, expectedArtifacts) } else { expectedArtifactIds: [expectedArtifacts.id] },
      withOrganization(organization):: self + { organization: organization },
      withRunAsUser(runAsUser):: self + { runAsUser: runAsUser },
      withRegistry(registry):: self + { registry: registry },
      withRepository(repository):: self + { repository: repository },
      withTag(tag):: self + { tag: tag },
    },
    git(name):: trigger(name, 'git') {
      withBranch(branch):: self + { branch: branch },
      withProject(project):: self + { project: project },
      withSlug(slug):: self + { slug: slug },
      withSource(source):: self + { source: source },
      withSecret(secret):: self + { secret: secret },
      withExpectedArtifacts(expectedArtifacts):: self + if std.type(expectedArtifacts) == 'array' then { expectedArtifactIds: std.map(function(expectedArtifact) expectedArtifact.id, expectedArtifacts) } else { expectedArtifactIds: [expectedArtifacts.id] },
    },
    webhook(name):: trigger(name, 'webhook') {
      payloadConstraints: {},
      withSource(source):: self + { source: source },
      addPayloadConstraint(key, value):: self + { payloadConstraints +: super.payloadConstraints + { [key]: value }},
    },
    pubsub(name, pubsubSystem, subscriptionName):: trigger(name, 'pubsub') {
      pubsubSystem: pubsubSystem,
      subscriptionName: subscriptionName,
      attributeConstraints: {},
      addAttributeConstraints(key, value):: self + { attributeConstraints+: { [key]: value } },
      payloadConstraints: {},
      addPayloadConstraints(key, value):: self + { payloadConstraints+: { [key]: value } },
    },
    cron(name, cronExpression):: trigger(name, 'cron') {
      cronExpression: cronExpression,
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
      withExecutionOptions(executionOptions)::
        assert std.type(executionOptions) == 'object': 'Execution options must now be an object';
        self + { executionOptions: executionOptions },
      withExpectedArtifact(expectedArtifact):: self + {
        expectedArtifact: {
          id: expectedArtifact.id,
          matchArtifact: expectedArtifact.matchArtifact,
        },
      },
      withPipeline(pipeline):: self + { pipeline: pipeline },
      withExpectedArtifacts(expectedArtifacts):: self + if std.type(expectedArtifacts) == 'array' then { expectedArtifacts: expectedArtifacts } else { expectedArtifacts: [expectedArtifacts] },
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
      withParameters(parameters):: self + { parameters: parameters},
      withPropertyFile(propertyFile):: self + { propertyFile: propertyFile },
      withMarkUnstableAsSuccessful(markUnstableAsSuccessful):: self + { markUnstableAsSuccessful: markUnstableAsSuccessful },
      withWaitForCompletion(waitForCompletion):: self + { waitForCompletion: waitForCompletion },
    },

    // kubernetes stages

    bakeManifest(name):: stage(name, 'bakeManifest') {
      templateRenderer: "HELM2",
      inputArtifacts: self.templateArtifact + self.valueArtifacts,
      overrides: {},
      templateArtifact:: [],
      valueArtifacts:: [],
      withExpectedArtifacts(artifacts):: self + if std.type(artifacts) == 'array' then { expectedArtifacts: [{ id: a.id, matchArtifact: a.matchArtifact } for a in artifacts] } else { expectedArtifacts: [{ id: artifacts.id, matchArtifacts: artifacts.matchArtifact }] },
      withNamespace(namespace):: self + { namespace: namespace },
      withReleaseName(name):: self + { outputName: name },
      withTemplateArtifact(artifact):: self + { templateArtifact:: [artifact] },
      withValueArtifacts(artifacts):: self + if std.type(artifacts) == 'array' then { valueArtifacts:: artifacts } else { valueArtifacts:: [artifacts] },
      withValueOverrides(overrides) :: self + { overrides: overrides },
      addValueOverride(key, value):: self + { overrides: super.overrides + { [key]: value} },
    },
    deployManifest(name):: stage(name, 'deployManifest') {
      cloudProvider: 'kubernetes',
      source: 'text',
      withAccount(account):: self + { account: account },
      withManifestArtifactAccount(account):: self + { manifestArtifactAccount: account },
      // Add/Default to embedded-artifact? If so add:  /* , manifestArtifactAccount: 'embedded-artifact' */
      withManifestArtifact(artifact):: self + { manifestArtifactId: artifact.id, source: 'artifact' },
      withRequiredArtifactIds(artifacts):: self + { requiredArtifactIds: [a.id for a in artifacts] },
      withManifests(manifests):: self + if std.type(manifests) == 'array' then { manifests: manifests } else { manifests: [manifests] },
      withMoniker(moniker):: self + { moniker: moniker },
      withSkipExpressionEvaluation():: self + { skipExpressionEvaluation: true },
    },
    deleteManifest(name):: stage(name, 'deleteManifest') {
      cloudProvider: 'kubernetes',
      options: {
        cascading: true,
      },
      withAccount(account):: self + { account: account },
      withKinds(kinds):: self + if std.type(kinds) == 'array' then { kinds: kinds } else { kinds: [kinds] },
      withNamespace(namespace):: self + { location: namespace },
      withLabelSelectors(selectors)::
        local selectorsArray = if std.type(selectors) == 'array' then selectors else [selectors];
        self + { mode: 'label', labelSelectors: { selectors: selectors } },
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
      withRevisionsBack(revisionsBack):: self + { numRevisionsBack: revisionsBack },
      withManifestName(kind, name):: self + { manifestName: kind + ' ' + name },
    },
    runJobManifest(name): stage(name, 'runJobManifest'){
      cloudProvider: 'kubernetes',
      alias: 'runJob',
      withAccount(account):: self + { account: account },
      withApplication(application):: self + { application: application },
      withManifestArtifactAccount(account):: self + { manifestArtifactAccount: account },
      withManifestArtifact(artifact):: self + { manifestArtifactId: artifact.id, source: 'artifact' },
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

    // Google Cloud Build stages

    googleCloudBuild(name): stage(name, 'googleCloudBuild') {
      withBuildDefinitionText(buildDefinition):: self + {
        buildDefinitionSource: 'text',
        buildDefinition: buildDefinition,
      },
      withAccount(account):: self + { account: account },
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
