local sponnet = import '../application.libsonnet';

sponnet.application()
.withName('demoappgce')
.withEmail('youremail@example.com')
.withCloudProviders('gce')
.withDescription('Demo sponnet application')
.withUser('youremail@example.com')
.withGceAssociatePublicIpAdddress(true)
.withInstancePort(80)
.withPlatformHealthOnly(true)
.withPlatformHealthOnlyShowOverride(true)

