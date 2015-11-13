1) add your aws key and secret to aws.json

2) `$ packer build aws.json`

3) launch your baked ami with spinnakerRole

4) ssh to your instance and tunnel 80 `ssh -L 9000:127.0.0.1:80 ubuntu@{SERVERIP}`

5) run configure script `chmod +x config.sh && sudo ./config.sh`

6) view spinnaker at http://localhost:9000