#!/bin/bash
set -e

echo "=== StockPulse Startup Script ==="

# Find PostgreSQL version and set paths
PG_VERSION=$(ls /usr/lib/postgresql/ | head -1)
PG_BIN="/usr/lib/postgresql/${PG_VERSION}/bin"
PG_DATA="/var/lib/postgresql/${PG_VERSION}/main"

echo "Using PostgreSQL version: ${PG_VERSION}"
echo "Binary path: ${PG_BIN}"
echo "Data path: ${PG_DATA}"

# Initialize PostgreSQL if needed
if [ ! -f "${PG_DATA}/PG_VERSION" ]; then
    echo "Initializing PostgreSQL..."
    mkdir -p "${PG_DATA}"
    chown -R postgres:postgres /var/lib/postgresql
    sudo -u postgres "${PG_BIN}/initdb" -D "${PG_DATA}"
    echo "host all all 0.0.0.0/0 md5" >> "${PG_DATA}/pg_hba.conf"
    echo "local all all trust" >> "${PG_DATA}/pg_hba.conf"
    echo "PostgreSQL initialized!"
fi

# Start PostgreSQL temporarily to setup database
echo "Starting PostgreSQL for initialization..."
sudo -u postgres "${PG_BIN}/pg_ctl" -D "${PG_DATA}" -l /tmp/pg.log start || true
sleep 5

# Create database and load data if needed
if ! sudo -u postgres psql -lqt | cut -d \| -f 1 | grep -qw stockpulse; then
    echo "Creating database..."
    sudo -u postgres createdb stockpulse || true
    echo "Loading schema..."
    sudo -u postgres psql stockpulse -f /app/init.sql || true
    echo "Loading data..."
    sudo -u postgres psql stockpulse -f /app/data.sql || true
    echo "Database created and data loaded!"
else
    echo "Database already exists, skipping initialization."
fi

# Stop PostgreSQL (supervisor will restart it)
echo "Stopping PostgreSQL for supervisor handoff..."
sudo -u postgres "${PG_BIN}/pg_ctl" -D "${PG_DATA}" stop || true
sleep 2

# Write the PostgreSQL paths for supervisor to use
echo "${PG_BIN}" > /tmp/pg_bin_path
echo "${PG_DATA}" > /tmp/pg_data_path

# Start all services via supervisor
echo "Starting supervisor..."
exec /usr/bin/supervisord -c /etc/supervisor/supervisord.conf
