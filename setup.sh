#!/bin/bash
echo "Setting up UI Customization Performance POC..."

# Make scripts executable
chmod +x start-oracle.sh
chmod +x start-cassandra.sh
chmod +x run-performance-tests.sh

# Create necessary directories
mkdir -p scripts
mkdir -p config
mkdir -p build/reports

# Start infrastructure
echo "Starting databases..."
docker-compose up -d

echo "Waiting for databases to initialize..."
sleep 60

echo "Setup complete! Use the following commands:"
echo "  ./start-oracle.sh     - Start with Oracle"
echo "  ./start-cassandra.sh  - Start with Cassandra"
echo "  ./run-performance-tests.sh - Run comprehensive tests"
echo ""
echo "Access points:"
echo "  Application: http://localhost:8080"
echo "  Actuator: http://localhost:8081/actuator"
echo "  Prometheus: http://localhost:9090"
echo "  Grafana: http://localhost:3000 (admin/admin)"