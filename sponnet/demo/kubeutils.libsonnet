{
  canary(deployment):: deployment {
    metadata+: {
      name+: "-canary"
    },
    spec+: {
      selector+: {
        matchLabels+: {
          "canary": "true",
        },
      },
      template+: {
        metadata+: {
          labels+: {
            "canary": "true",
          },
        },
      },
    },
  }
}

