#!/bin/bash
echo "Starting Cassandra environment..."
docker-compose up -d cassandra prometheus grafana
echo "Waiting for Cassandra to be ready..."
docker-compose exec cassandra cqlsh -e "SELECT 'Cassandra is ready' FROM system.local;"
echo "Starting application with Cassandra profile..."
./gradlew bootRun --args='--spring.profiles.active=cassandra'