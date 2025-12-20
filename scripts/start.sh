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

# Write the PostgreSQL paths for supervisor to use
echo "${PG_BIN}" > /tmp/pg_bin_path
echo "${PG_DATA}" > /tmp/pg_data_path

# Clean up any stale data directory
if [ -d "${PG_DATA}" ] && [ ! -f "${PG_DATA}/postgresql.conf" ]; then
    echo "Cleaning up incomplete PostgreSQL data directory..."
    rm -rf "${PG_DATA}"
fi

# Initialize PostgreSQL if needed
if [ ! -f "${PG_DATA}/postgresql.conf" ]; then
    echo "Initializing PostgreSQL..."
    mkdir -p "${PG_DATA}"
    chown -R postgres:postgres /var/lib/postgresql
    chmod 700 "${PG_DATA}"
    sudo -u postgres "${PG_BIN}/initdb" -D "${PG_DATA}" --encoding=UTF8 --locale=C

    # Configure authentication
    echo "host all all 0.0.0.0/0 md5" >> "${PG_DATA}/pg_hba.conf"
    echo "host all all ::0/0 md5" >> "${PG_DATA}/pg_hba.conf"
    sed -i "s/#listen_addresses = 'localhost'/listen_addresses = '*'/" "${PG_DATA}/postgresql.conf"

    echo "PostgreSQL initialized!"

    # Start PostgreSQL to create database
    echo "Starting PostgreSQL to create database..."
    sudo -u postgres "${PG_BIN}/pg_ctl" -D "${PG_DATA}" -l /tmp/pg.log start
    sleep 5

    # Create database and load data
    echo "Creating database..."
    sudo -u postgres createdb stockpulse
    echo "Loading schema..."
    sudo -u postgres psql stockpulse -f /app/init.sql || true
    echo "Loading data..."
    sudo -u postgres psql stockpulse -f /app/data.sql || true
    echo "Database created and data loaded!"

    # Stop PostgreSQL (supervisor will restart it)
    echo "Stopping PostgreSQL for supervisor handoff..."
    sudo -u postgres "${PG_BIN}/pg_ctl" -D "${PG_DATA}" stop
    sleep 2
else
    echo "PostgreSQL already initialized, skipping..."
fi

# Start all services via supervisor
echo "Starting supervisor..."
exec /usr/bin/supervisord -c /etc/supervisor/supervisord.conf
