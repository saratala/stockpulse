import logging
from time import sleep
import subprocess
from datetime import datetime

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def run_etl_jobs():
    jobs = [
        "etl_finance.py",
        "sentiment_vader.py",
        "predict_engine.py",
        "predict_daily.py"
    ]
    
    for job in jobs:
        try:
            logger.info(f"Running {job} at {datetime.now()}")
            result = subprocess.run(["python", job], check=True)
            if result.returncode == 0:
                logger.info(f"✅ {job} completed successfully")
            else:
                logger.error(f"❌ {job} failed with code {result.returncode}")
        except Exception as e:
            logger.error(f"❌ Error running {job}: {str(e)}")

if __name__ == "__main__":
    logger.info("Starting ETL service...")
    while True:
        run_etl_jobs()
        logger.info("All jobs completed. Sleeping for 1 hour...")
        sleep(3600)  # Sleep for 1 hour between runs