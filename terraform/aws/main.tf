# Specify the provider and access details
provider "aws" {
  region = "${var.aws_region}"
}

module "elasticache" {
  source = "./modules/elasticache"
  ecs_security_group = "${module.ecs.ecs_security_group_id}"
}

module "ecs" {
  source = "./modules/ecs"
}

module "clouddriver" {
  source = "./modules/clouddriver"
  elasticache_cluster_address = "${module.elasticache.address}"
  iam_role_arn = "${aws_iam_role.ecs_task_role.arn}"
  front50_elb_dns_name = "${module.front50.elb_dns_name}"
}

module "front50" {
  source = "./modules/front50"
  iam_role_arn = "${aws_iam_role.ecs_task_role.arn}"
}

resource "aws_instance" "ecs-001" {
    ami = "ami-478b9177"
    instance_type = "m4.xlarge"
    iam_instance_profile = "${module.ecs.instance_profile_id}"
    key_name = "spinnaker-keypair"
    security_groups = [
      "${module.ecs.ecs_security_group}"
    ]
    tags {
        Name = "ecs-001"
    }
}

resource "aws_iam_role" "ecs_task_role" {
    name = "ecs_task_role"
    assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": [
          "ec2.amazonaws.com",
          "ecs.amazonaws.com"
        ]
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

resource "aws_iam_role_policy" "ecs_task_policy" {
    name = "ecs_task_policy"
    role = "${aws_iam_role.ecs_task_role.id}"
    policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "*"
      ],
      "Effect": "Allow",
      "Resource": "*"
    }
  ]
}
EOF
}