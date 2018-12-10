{
  application():: {
    withAccounts(accounts):: self + if std.type(accounts) == 'array' then { accounts: accounts } else { accounts: [accounts] },
    withAliases(aliases):: self + { aliases: aliases },
    withClusters(clusters):: self + if std.type(clusters) == 'array' then { clusters: clusters } else { clusters: [clusters] },
    withCloudProviders(cloudProviders):: self + if std.type(cloudProviders) == 'array' then { cloudProviders: cloudProviders } else { cloudProviders: [cloudProviders] },
    withDescription(description):: self + { attributes+: { description: description } },
    // Email address must be put in both root and attributes for Spin to save application.
    withEmail(email):: self + { email: email } + { attributes+: { email: email } },
    withInstancePort(port):: self + { attributes+: { instancePort: port } },
    withName(name):: self + { name: name },
    withPlatformHealthOnly(platformHealthOnly):: self + { platformHealthOnly: platformHealthOnly },
    withUser(user):: self + { attributes+: { user: user } },
  },
}
