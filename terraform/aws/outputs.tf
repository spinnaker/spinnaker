output "address" {
  value = "${aws_elb.clouddriver.dns_name}"
}

output "ecs-001" {
  value = "${aws_instance.ecs-001.public_ip}"
}
