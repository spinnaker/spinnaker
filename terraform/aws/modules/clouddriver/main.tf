variable "elasticache_cluster_address" {}
variable "iam_role_arn" {}
variable "front50_elb_dns_name" {}

output "elb_dns_name" {
  value = "${aws_elb.clouddriver.dns_name}"
}

resource "aws_ecs_service" "clouddriver" {
  name = "clouddriver"
  cluster = "default"
  task_definition = "${aws_ecs_task_definition.clouddriver.arn}"
  desired_count = 1
  iam_role = "${var.iam_role_arn}"
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
      "memory": 3096,
      "essential": true,
      "environment": [
        { "name": "AWS_ENABLED", "value": "true" },
        { "name": "REDIS_CONNECTION", "value": "${format("redis://%s:6379", var.elasticache_cluster_address)}" },
        { "name": "SERVICES_FRONT50_BASEURL", "value": "${format("http://%s:7001", var.front50_elb_dns_name)}" }
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

resource "aws_elb" "clouddriver" {
  name = "clouddriver-main-elb"
  availability_zones = ["us-west-2a", "us-west-2b", "us-west-2c"]

  listener {
    instance_port = 7002
    instance_protocol = "http"
    lb_port = 7001
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