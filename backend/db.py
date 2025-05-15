
import os
import time
from dotenv import load_dotenv
from sqlalchemy import create_engine, text

# Load environment variables from .env
load_dotenv()
DATABASE_URL = os.getenv("DATABASE_URL")

def get_engine_with_retry(retries=10, delay=3):
    """
    Attempt to connect to the database with retries and delay.
    Returns a SQLAlchemy engine if successful.
    """
    engine = create_engine(DATABASE_URL, pool_pre_ping=True)

    for attempt in range(1, retries + 1):
        try:
            with engine.begin() as conn:
                conn.execute(text("SELECT 1"))
            print("✅ Database connected.")
            return engine
        except Exception as e:
            print(f"⏳ Attempt {attempt}/{retries}: Waiting for database... ({e})")
            time.sleep(delay)

    raise RuntimeError("❌ Could not connect to the database after retries.")
