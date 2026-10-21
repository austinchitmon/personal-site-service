#!/bin/sh
set -eu
source_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ "$(id -u)" -ne 0 ]; then
  echo 'Run this installer with sudo on the homelab.' >&2
  exit 1
fi
install -d -o root -g root -m 755 /usr/local/lib/chitmon-monitor /var/lib/chitmon-monitor
install -o root -g root -m 644 "$source_dir/collect.py" /usr/local/lib/chitmon-monitor/collect.py
install -o root -g root -m 644 "$source_dir/chitmon-monitor.service" /etc/systemd/system/chitmon-monitor.service
install -o root -g root -m 644 "$source_dir/chitmon-monitor.timer" /etc/systemd/system/chitmon-monitor.timer
systemctl daemon-reload
systemctl start chitmon-monitor.service
systemctl enable --now chitmon-monitor.timer
echo 'Collector installed. Existing application, database and tunnel services were not restarted.'
