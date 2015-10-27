output "instance_profile_id" {
  value = "${aws_iam_instance_profile.ecsInstanceProfile.id}"
}

output "ecs_security_group" {
  value = "${aws_security_group.ecs-sg.name}"
}

output "ecs_security_group_id" {
  value = "${aws_security_group.ecs-sg.id}"
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
