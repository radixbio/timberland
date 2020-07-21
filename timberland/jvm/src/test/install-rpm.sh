#!/usr/bin/env bash

docker swarm init
yes | docker plugin install weaveworks/net-plugin:2.6.0
docker plugin disable weaveworks/net-plugin:2.6.0
docker plugin set weaveworks/net-plugin:2.6.0 IPALLOC_RANGE=10.32.0.0/12
docker plugin enable weaveworks/net-plugin:2.6.0

yum install -y ./timberland-rpm-amd64.rpm

cd /opt/radix/timberland/exec
docker network create --attachable -d weaveworks/net-plugin:2.6.0 weave
echo -n "nE-3KEuKQN3rrnxWkMH1" | docker login registry.gitlab.com -u radix-timberland-ci --password-stdin
./timberland runtime enable all
./timberland runtime disable elemental
./timberland runtime start

sleep 3

/home/centos/service_test.py | tee /tmp/service_test.log

EXIT_CODE=$?

echo "Service test exit code: $EXIT_CODE"

mkdir /home/centos/nomad-logs
rsync -av --relative /opt/radix/nomad/alloc/./*/alloc/logs/* /home/centos/nomad-logs

exit $EXIT_CODE
