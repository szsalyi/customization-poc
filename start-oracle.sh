#!/bin/bash
echo "Starting Oracle environment..."
docker-compose up -d oracle prometheus grafana
echo "Waiting for Oracle to be ready..."
docker-compose exec oracle sqlplus -L sys/OraclePwd123@//localhost:1521/XE as sysdba <<< "SELECT 'Oracle is ready' FROM DUAL;"
echo "Starting application with Oracle profile..."
./gradlew bootRun --args='--spring.profiles.active=oracle'