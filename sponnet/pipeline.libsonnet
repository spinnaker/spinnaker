{
  pipeline():: {
    limitConcurrent: true,
    keepWaitingPipelines: false,
    stages: [],
    triggers: [],
    withApplication(application):: self + {application: application},
    withName(name):: self + {name: name},
    withStages(stages):: self + if std.type(stages) == "array" then {stages: stages} else {stages: [stages]},
    withTriggers(triggers):: self + if std.type(triggers) == "array" then {triggers: triggers} else {triggers: [triggers]},
  },

  moniker(app, cluster):: {
    app: app,
    cluster: cluster,
    withStack(stack):: self + {stack: stack},
    withDetail(detail):: self + {detail: detail},
  },

  // artifacts

  artifact(type):: {
    type: type,
    withName(name):: self + {name: name},
    withVersion(version):: self + {version: version},
    withReference(reference):: self + {reference: reference},
    withLocation(location):: self + {location: location},
  },

  local artifact = self.artifact,
  artifacts:: {
    dockerImage():: artifact("docker/image"),
    githubFile():: artifact("github/file"),
    gcsObject():: artifact("gcs/object"),
  },

  expectedArtifact(id):: {
    id: id,
    withMatchArtifact(matchArtifact):: self + {matchArtifact: matchArtifact},
    withDefaultArtifact(defaultArtifact):: self + {defaultArtifact: defaultArtifact},
    withUsePriorArtifact(usePriorArtifact):: self + {usePriorArtifact: usePriorArtifact},
    withUseDefaultArtifact(useDefaultArtifact):: self + {useDefaultArtifact: useDefaultArtifact},
  },

  // triggers

  trigger():: {
    enabled: true,
    withType(type):: self + {type: type},
    withExpectedArtifacts(expectedArtifacts):: self + if std.type(expectedArtifacts) == "array" then {expectedArtifactIds: std.map(function(expectedArtifact) expectedArtifact.id, expectedArtifacts)} else {expectedArtifactIds: [expectedArtifacts.id]},
  },

  // stages

  stage(name, type):: {
    refId: name,
    name: name,
    type: type,
    requisiteStageRefIds: [],
    withRequisiteStages(stages):: self + if std.type(stages) == "array" then {requisiteStageRefIds: std.map(function(stage) stage.refId, stages)} else {requisiteStageRefIds: [stages.refId]}
  },

  local stage = self.stage,
  stages:: {
    wait(name):: stage(name, "wait") {
      withWaitTime(waitTime):: self + {waitTime: waitTime},
    },

    // kubernetes stages

    deploy_manifest(name):: stage(name, "deployManifest") {
      cloudProvider: "kubernetes",
      source: "text",
      withAccount(account):: self + {account: account},
      withManifests(manifests):: self + if std.type(manifests) == "array" then {manifests: manifests} else {manifests: [manifests]},
      withMoniker(moniker):: self + {moniker: moniker},
    },
    delete_manifest(name):: stage(name, "deleteManifest") {
      cloudProvider: "kubernetes",
      options: {
        cascading: true,
      },
      withAccount(account):: self + {account: account},
      withKinds(kinds):: self + if std.type(kinds) == "array" then {kinds: kinds} else {kinds: [kinds]},
      withNamespace(namespace):: self + {location: namespace},
      withLabelSelectors(selectors):: self + if std.type(selectors) == "array" then {labelSelectors: {selectors: selectors}} else {labelSelectors: {selectors: [selectors]}},
      withGracePeriodSeconds(seconds):: self.options + {gracePeriodSeconds: seconds},
      withManifestName(kind, name):: self.options + {manifestName: kind + " " + name},
    },
    patch_manifest(name):: stage(name, "patchManifest") {
      cloudProvider: "kubernetes",
      source: "text",
      options: {
        mergeStrategy: "strategic",
        record: true,
      },
      withAccount(account):: self + {account: account},
      withNamespace(namespace):: self + {location: namespace},
      withPatchBody(patchBody): self + {patchBody: patchBody},
      withManifestName(kind, name):: self.options + {manifestName: kind + " " + name},
    },
    scale_manifest(name): stage(name, "scaleManifest") {
      cloudProvider: "kubernetes",
      withAccount(account):: self + {account: account},
      withNamespace(namespace):: self + {location: namespace},
      withReplicas(replicas): self + {replicas: replicas},
      withManifestName(kind, name):: self.options + {manifestName: kind + " " + name},
    },
    undo_rollout_manifest(name): stage(name, "undoRolloutManifest") {
      cloudProvider: "kubernetes",
      withAccount(account):: self + {account: account},
      withNamespace(namespace):: self + {location: namespace},
      withRevisionsBack(revisionsBack): self + {numRevisionsBack: revisionsBack},
      withManifestName(kind, name):: self.options + {manifestName: kind + " " + name},
    },
  },

  // kubernetes-provider help

  kubernetes:: {
    selector(kind):: {
      kind: kind,
    },
    local selector = self.selector,
    anySelector(key, value):: selector("ANY"),
    equalsSelector(key, value):: selector("EQUALS") {
      key: key,
      values: [value],
    },
    notEqualsSelector(key, value):: selector("NOT_EQUALS") {
      key: key,
      values: [value],
    },
    containsSelector(key, values):: selector("CONTAINS") {
      key: key,
      values: values,
    },
    notContainsSelector(key, values):: selector("NOT_CONTAINS") {
      key: key,
      values: values,
    },
    existsSelector(key):: selector("EXISTS") {
      key: key,
    },
    notExistsSelector(key):: selector("NOT_EXISTS") {
      key: key,
    },
  }
}
