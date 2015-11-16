Ensure that packer is on your PATH https://www.packer.io/downloads.html

1) add your aws key and secret to aws.json

2) `$ packer build aws.json`

3) launch your baked ami in us-west-2 as a m4.xlarge, IAM role: spinnakerRole. Ensure the security group allows your ip to ssh in.

4) ssh to your instance and tunnel 80 `ssh -L 9000:127.0.0.1:80 -L 9999:127.0.0.1:9999 ubuntu@{SERVERIP}`

5) run configure script `sudo ~/config.sh` you can just press enter to accept defaults when prompted. It is already set up for our vpc in us-west-2

6) spinnaker will be running on http://localhost:9000 and jenkins will be on http://localhost:9999