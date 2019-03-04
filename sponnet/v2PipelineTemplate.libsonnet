{
    pipelineTemplate():: {
        schema: 'v2',
        protect: false,
        variables: [],
        withId(id):: self + { id: id },
        withProtect(protect):: self + { protect: protect },
        withMetadata(metadata):: self + { metadata: metadata },
        withVariables(variables):: self + if std.type(variables) == 'array' then { variables: variables } else { variables: [variables] },
        withPipeline(pipeline):: self + { pipeline: pipeline },
    },

    metadata():: {
        scopes: [],
        withName(name):: self + { name: name },
        withDescription(description):: self + { description: description },
        withOwner(owner):: self + { owner: owner },
        withScopes(scopes):: self + if std.type(scopes) == 'array' then { scopes: scopes } else { scopes: [scopes] },
    },

    variable():: {
        withType(type):: self + { type: type },
        withDefaultValue(value):: self + { defaultValue: value },
        withDescription(description):: self + { description: description },
        withName(name):: self + { name: name },
    },
}
