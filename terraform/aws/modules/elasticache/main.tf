variable "ecs_security_group" {}

output "address" {
    value = "${aws_elasticache_cluster.elasticache.cache_nodes.0.address}"
}

resource "aws_security_group" "elasticache-sg" {
  name = "elasticache-sg"
  description = "Allow all inbound traffic from ecs-sg"

  ingress {
      from_port = 0
      to_port = 0
      protocol = "-1"
      security_groups = [
        "${var.ecs_security_group.id}"
      ]
  }

  egress {
      from_port = 0
      to_port = 0
      protocol = "-1"
      cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_elasticache_cluster" "elasticache" {
    cluster_id = "elasticache-main"
    engine = "redis"
    node_type = "cache.m3.large"
    port = 6379
    num_cache_nodes = 1
    parameter_group_name = "default.redis2.8"
    security_group_ids = ["${aws_security_group.elasticache-sg.id}"]
}
