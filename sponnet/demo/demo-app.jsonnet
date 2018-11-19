local sponnet = import '../application.libsonnet';

sponnet.application()
.withName('myapp')
.withEmail('youremail@example.com')
.withCloudProviders('kubernetes')
.withDescription('Demo sponnet application')
.withUser('youremail@example.com')
