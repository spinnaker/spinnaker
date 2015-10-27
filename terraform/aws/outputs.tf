output "clouddriver_elb_dns_name" {
  value = "${module.clouddriver.elb_dns_name}"
}

output "front50_elb_dns_name" {
  value = "${module.front50.elb_dns_name}"
}

output "ecs-001" {
  value = "${aws_instance.ecs-001.public_ip}"
}
