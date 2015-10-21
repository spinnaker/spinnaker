# ECS - Clouddriver

This example launches/creates:
- an ecs instance
- an ecs service / task definition for Clouddriver
- an ELB associated with the Clouddriver service
- an elasticache redis cluster for use by Clouddriver
- various iam roles and security groups as needed for ECS/Elasticache/Spinnaker

Requirements:
- configure your AWS provider as described in https://www.terraform.io/docs/providers/aws/index.html
- create a suitable keypair (spinnaker-keypair) via the AWS console

Run this example using:

    terraform apply