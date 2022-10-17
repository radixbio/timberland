#!/usr/bin/env bash

systemctl daemon-reload
systemctl enable nomad consul vault timberland-svc timberland-after-startup

sysctl -w vm.max_map_count=262144

mkdir -p /opt/radix/terraform
mkdir -p /opt/radix/zookeeper_data
mkdir -p /opt/radix/kafka_data
mkdir -p /opt/radix/ybmaster_data
mkdir -p /opt/radix/ybtserver_data
mkdir -p /opt/radix/elasticsearch_data
mkdir -p /opt/radix/interface/build
mkdir -p /opt/radix/services

if [ -f /opt/radix/timberland/exec/timberland ]; then
    cd /opt/radix/timberland/exec/
    ./timberland dns up
    ./timberland make_config
fi

# WARNING: This following actions are not idempotent!
# See: https://www.debian.org/doc/debian-policy/ch-maintainerscripts.html

echo "vm.max_map_count=262144" >> /etc/sysctl.conf
