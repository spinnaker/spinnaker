variable "iam_role_arn" {}

output "elb_dns_name" {
  value = "${aws_elb.front50.dns_name}"
}

resource "aws_ecs_service" "front50" {
  name = "front50"
  cluster = "default"
  task_definition = "${aws_ecs_task_definition.front50.arn}"
  desired_count = 1

  iam_role = "${var.iam_role_arn}"
  load_balancer {
    elb_name = "${aws_elb.front50.id}"
    container_name = "front50"
    container_port = 7002
  }
}

resource "aws_ecs_task_definition" "front50" {
  family = "front50"
  container_definitions = <<EOF
  [
    {
      "name": "front50",
      "image": "spinnaker/front50",
      "cpu": 1024,
      "memory": 1024,
      "essential": true,
      "environment": [
        { "name": "SERVER_PORT", "value": "7002" },
        { "name": "JAVA_OPTS", "value": "-javaagent:/opt/front50/lib/jamm-0.2.5.jar" }
      ],
      "portMappings": [
        {
          "containerPort": 7002,
          "hostPort": 7003
        }
      ]
    }
  ]
EOF
}

resource "aws_elb" "front50" {
  name = "front50-main-elb"
  availability_zones = ["us-west-2a", "us-west-2b", "us-west-2c"]

  listener {
    instance_port = 7003
    instance_protocol = "http"
    lb_port = 7001
    lb_protocol = "http"
  }

  cross_zone_load_balancing = true
  idle_timeout = 400
  connection_draining = true
  connection_draining_timeout = 400

  tags {
    Name = "front50-terraform-elb"
  }
}