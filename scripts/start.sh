#!/bin/bash
set -e

echo "=== StockPulse Startup Script ==="

# Initialize PostgreSQL if needed
if [ ! -f /var/lib/postgresql/14/main/PG_VERSION ]; then
    echo "Initializing PostgreSQL..."
    mkdir -p /var/lib/postgresql/14/main
    chown -R postgres:postgres /var/lib/postgresql
    sudo -u postgres /usr/lib/postgresql/14/bin/initdb -D /var/lib/postgresql/14/main
    echo "host all all 0.0.0.0/0 md5" >> /var/lib/postgresql/14/main/pg_hba.conf
    echo "local all all trust" >> /var/lib/postgresql/14/main/pg_hba.conf
    echo "PostgreSQL initialized!"
fi

# Start PostgreSQL temporarily to setup database
echo "Starting PostgreSQL for initialization..."
sudo -u postgres /usr/lib/postgresql/14/bin/pg_ctl -D /var/lib/postgresql/14/main -l /tmp/pg.log start || true
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
sudo -u postgres /usr/lib/postgresql/14/bin/pg_ctl -D /var/lib/postgresql/14/main stop || true
sleep 2

# Start all services via supervisor
echo "Starting supervisor..."
exec /usr/bin/supervisord -c /etc/supervisor/supervisord.conf
