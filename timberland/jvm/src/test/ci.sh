#!/usr/bin/env bash

AWS_RESULT=$(aws ec2 run-instances --image-id ami-06eebf06f35297979 --instance-type t3.2xlarge --security-group-ids sg-0c06080d080b5876f --associate-public-ip-address --key-name radix-ci --instance-initiated-shutdown-behavior terminate)
AWS_INSTANCE_ID=$(echo $AWS_RESULT | jq -r .Instances[0].InstanceId)

echo "Waiting for EC2 instance to become ready..."

aws ec2 wait instance-running --instance-ids $AWS_INSTANCE_ID

AWS_INSTANCE_IP=$(aws ec2 describe-instances --instance-ids $AWS_INSTANCE_ID | jq -r .Reservations[0].Instances[0].PublicIpAddress)
AWS_INSTANCE_SSH_USER=ubuntu

function test_ssh {
  nc -w 2 -z $AWS_INSTANCE_IP 22
  echo $?
}

while [ $(test_ssh) -ne 0 ]
do
  echo "Waiting for SSH..."
  sleep 2
done

echo "Transferring deb to instance ($AWS_INSTANCE_IP)..."

scp -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" ./timberland/jvm/radix-timberland_0.1_all.deb $AWS_INSTANCE_SSH_USER@$AWS_INSTANCE_IP:~

scp -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" ./timberland/jvm/src/test/service_test.py $AWS_INSTANCE_SSH_USER@$AWS_INSTANCE_IP:~

ssh -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" $AWS_INSTANCE_SSH_USER@$AWS_INSTANCE_IP 'sudo su && bash -s' <<'ENDSSH'
docker swarm init
dpkg -i radix-timberland_0.1_all.deb
cd /opt/radix/timberland/exec
./timberland runtime install
docker network create --attachable -d weaveworks/net-plugin:2.6.0 weave
echo -n "Vu6nzjx8T_sy14pxrepu" | docker login registry.gitlab.com -u radix-timberland-ci --password-stdin
./timberland runtime start --dev &

sleep 8

# Consul and Nomad do not always start up correctly via "runtime start". It may be a race condition related to the setting of
# environment variables and the starting of the services. They are started here just in case.
#
# 2020.03.10 19:50:45 INFO com.radix.timberland.runtime.Run.RuntimeServicesExec.startConsul:168:20 - spawning consul via systemd
# 2020.03.10 19:50:45 INFO com.radix.timberland.runtime.Run.RuntimeServicesExec.startNomad:188:20 - spawning nomad via systemd
# started consul and nomad
# ((),())
# 2020.03.10 19:50:44 INFO com.radix.timberland.runner.main.cmdEval:569:32 - Launching daemons
# 2020.03.10 19:50:44 INFO com.radix.timberland.runner.main.cmdEval:570:32 - ***********DAEMON SIZE1***************
# Job for consul.service failed because the control process exited with error code.
# See "systemctl status consul.service" and "journalctl -xe" for details.
# Job for nomad.service failed.
# See "systemctl status nomad.service" and "journalctl -xe" for details.

systemctl start consul
systemctl start nomad

sleep 2

/home/ubuntu/service_test.py

EXIT_CODE=$?

echo "Service test status code: $EXIT_CODE"

nohup sleep 3 && poweroff &

exit $EXIT_CODE

ENDSSH
