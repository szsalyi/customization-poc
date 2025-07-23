#!/bin/bash
echo "Running comprehensive performance tests..."

echo "Testing Oracle..."
docker-compose up -d oracle
sleep 30
./gradlew clean performanceTest -Dspring.profiles.active=oracle,performance
mv build/reports/tests/performanceTest build/reports/oracle-performance

echo "Testing Cassandra..."
docker-compose up -d cassandra
sleep 30
./gradlew clean performanceTest -Dspring.profiles.active=cassandra,performance
mv build/reports/tests/performanceTest build/reports/cassandra-performance

echo "Generating comparison report..."
./gradlew generatePerformanceReport