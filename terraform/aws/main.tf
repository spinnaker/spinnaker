# Specify the provider and access details
provider "aws" {
  region = "${var.aws_region}"
}

resource "aws_instance" "ecs-001" {
    ami = "ami-478b9177"
    instance_type = "c4.large"
    iam_instance_profile = "${aws_iam_instance_profile.ecsInstanceProfile.id}"
    key_name = "spinnaker-keypair"
    security_groups = [
      "${aws_security_group.ecs-sg.name}"
    ]
    tags {
        Name = "ecs-001"
    }
    depends_on = [
      "aws_iam_instance_profile.ecsInstanceProfile"
    ]
}

resource "aws_ecs_service" "clouddriver" {
  name = "clouddriver"
  cluster = "default"
  task_definition = "${aws_ecs_task_definition.clouddriver.arn}"
  desired_count = 1
  iam_role = "${aws_iam_role.clouddriver.arn}"
  depends_on = [
    "aws_iam_role_policy.clouddriver"
  ]

  load_balancer {
    elb_name = "${aws_elb.clouddriver.id}"
    container_name = "clouddriver"
    container_port = 7002
  }
}

resource "aws_ecs_task_definition" "clouddriver" {
  family = "clouddriver"
  container_definitions = <<EOF
  [
    {
      "name": "clouddriver",
      "image": "spinnaker/clouddriver",
      "cpu": 1024,
      "memory": 1536,
      "essential": true,
      "environment": [
        { "name": "AWS_ENABLED", "value": "true" },
        { "name": "REDIS_CONNECTION", "value": "${format("redis://%s:6379", aws_elasticache_cluster.clouddriver.cache_nodes.0.address)}" }
      ],
      "portMappings": [
        {
          "containerPort": 7002,
          "hostPort": 7002
        }
      ]
    }
  ]
EOF
}

resource "aws_iam_role_policy" "clouddriver" {
    name = "clouddriver"
    role = "${aws_iam_role.clouddriver.id}"
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

resource "aws_iam_role" "clouddriver" {
    name = "clouddriver"
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

resource "aws_iam_instance_profile" "ecsInstanceProfile" {
    name = "ecsInstanceProfile"
    roles = ["${aws_iam_role.ecsInstanceRole.name}"]
}

resource "aws_iam_role_policy" "ecsInstanceRole" {
    name = "ecsInstanceRole"
    role = "${aws_iam_role.ecsInstanceRole.id}"
    policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecs:CreateCluster",
        "ecs:DeregisterContainerInstance",
        "ecs:DiscoverPollEndpoint",
        "ecs:Poll",
        "ecs:RegisterContainerInstance",
        "ecs:StartTelemetrySession",
        "ecs:Submit*",
        "*"
      ],
      "Resource": "*"
    }
  ]
}
EOF
}

resource "aws_iam_role" "ecsInstanceRole" {
    name = "ecsInstanceRole"
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

resource "aws_elb" "clouddriver" {
  name = "clouddriver-main-elb"
  availability_zones = ["us-west-2a", "us-west-2b", "us-west-2c"]

  listener {
    instance_port = 7002
    instance_protocol = "http"
    lb_port = 7002
    lb_protocol = "http"
  }

  cross_zone_load_balancing = true
  idle_timeout = 400
  connection_draining = true
  connection_draining_timeout = 400

  tags {
    Name = "clouddriver-terraform-elb"
  }
}

resource "aws_security_group" "ecs-sg" {
  name = "ecs-sg"
  description = "Allow all inbound traffic"

  ingress {
      from_port = 0
      to_port = 0
      protocol = "-1"
      cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
      from_port = 0
      to_port = 0
      protocol = "-1"
      cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "elasticache-sg" {
  name = "elasticache-sg"
  description = "Allow all inbound traffic from ecs-sg"

  ingress {
      from_port = 0
      to_port = 0
      protocol = "-1"
      security_groups = [
        "${aws_security_group.ecs-sg.id}"
      ]
  }

  egress {
      from_port = 0
      to_port = 0
      protocol = "-1"
      cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_elasticache_cluster" "clouddriver" {
    cluster_id = "clouddriver-main"
    engine = "redis"
    node_type = "cache.m3.large"
    port = 6379
    num_cache_nodes = 1
    parameter_group_name = "default.redis2.8"
    security_group_ids = ["${aws_security_group.elasticache-sg.id}"]
}