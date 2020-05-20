#!/usr/bin/env bash

systemctl daemon-reload
systemctl restart systemd-networkd

sysctl -w vm.max_map_count=262144

yes | docker plugin install weaveworks/net-plugin:2.6.0
docker plugin disable weaveworks/net-plugin:2.6.0
docker plugin set weaveworks/net-plugin:2.6.0 IPALLOC_RANGE=10.32.0.0/12
docker plugin enable weaveworks/net-plugin:2.6.0

# WARNING: This following actions are not idempotent!
# See: https://www.debian.org/doc/debian-policy/ch-maintainerscripts.html

echo "vm.max_map_count=262144" >> /etc/sysctl.conf
