"""
Daily Scheduler for Stock Screening and Signal Detection
Runs the screening pipeline daily before market open
"""

import schedule
import time
import logging
from datetime import datetime, timedelta
import json
import os
from typing import Dict, List, Optional
from sqlalchemy import create_engine, text
from dotenv import load_dotenv
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
import requests
from concurrent.futures import ThreadPoolExecutor

# Import our modules
from screener_module import StockScreener
from heikin_ashi_signals import HeikinAshiSignalDetector
from enhanced_data_fetcher import EnhancedDataFetcher

# Load environment variables
load_dotenv()

# Set up logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('scheduler.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

class DailyScheduler:
    """
    Daily scheduler for running stock screening and signal detection
    """
    
    def __init__(self):
        """Initialize the daily scheduler"""
        self.db_engine = None
        if os.getenv("DATABASE_URL"):
            self.db_engine = create_engine(os.getenv("DATABASE_URL"))
        
        # Initialize modules
        self.screener = StockScreener(self.db_engine)
        self.data_fetcher = EnhancedDataFetcher(self.db_engine)
        self.signal_detector = HeikinAshiSignalDetector(self.data_fetcher)
        
        # Configuration
        self.config = {
            'min_screening_score': int(os.getenv('MIN_SCREENING_SCORE', '70')),
            'max_stocks_to_analyze': int(os.getenv('MAX_STOCKS_TO_ANALYZE', '100')),
            'notification_email': os.getenv('NOTIFICATION_EMAIL'),
            'smtp_server': os.getenv('SMTP_SERVER'),
            'smtp_port': int(os.getenv('SMTP_PORT', '587')),
            'smtp_username': os.getenv('SMTP_USERNAME'),
            'smtp_password': os.getenv('SMTP_PASSWORD'),
            'slack_webhook': os.getenv('SLACK_WEBHOOK'),
            'discord_webhook': os.getenv('DISCORD_WEBHOOK')
        }
        
        self.results_storage = []
    
    def run_daily_screening(self) -> Dict:
        """
        Run the complete daily screening pipeline
        """
        logger.info("Starting daily screening pipeline...")
        
        try:
            # 1. Run stock screening
            logger.info("Running stock screening...")
            screening_results = self.screener.run_screening()
            
            # 2. Filter top candidates
            top_candidates = self.screener.get_top_candidates(
                screening_results, 
                min_score=self.config['min_screening_score']
            )
            
            # 3. Get tickers for signal analysis
            candidate_tickers = [stock['ticker'] for stock in top_candidates[:self.config['max_stocks_to_analyze']]]
            
            # 4. Run Heikin Ashi signal detection
            logger.info(f"Running signal detection on {len(candidate_tickers)} candidates...")
            signal_results = self.signal_detector.scan_multiple_stocks(candidate_tickers)
            
            # 5. Generate reports
            screening_report = self.screener.generate_screening_report(screening_results)
            signal_report = self.signal_detector.generate_signal_report(signal_results)
            
            # 6. Combine results
            combined_results = self._combine_results(screening_report, signal_report)
            
            # 7. Store results
            self._store_results(combined_results)
            
            # 8. Send notifications
            self._send_notifications(combined_results)
            
            logger.info("Daily screening pipeline completed successfully")
            return combined_results
            
        except Exception as e:
            logger.error(f"Error in daily screening pipeline: {e}")
            self._send_error_notification(str(e))
            raise
    
    def _combine_results(self, screening_report: Dict, signal_report: Dict) -> Dict:
        """
        Combine screening and signal detection results
        """
        # Get high-quality signals
        high_quality_signals = []
        
        for signal in signal_report['all_results']:
            if signal.get('primary_signal') in ['BULLISH', 'BEARISH'] and signal.get('primary_confidence', 0) >= 60:
                # Find corresponding screening data
                ticker = signal['ticker']
                screening_data = None
                
                for stock in screening_report['all_results']:
                    if stock['ticker'] == ticker:
                        screening_data = stock
                        break
                
                if screening_data:
                    combined_signal = {
                        **signal,
                        'screening_score': screening_data['screening_score'],
                        'passes_all_screens': screening_data['overall_pass'],
                        'market_cap': screening_data['market_cap'],
                        'sector': screening_data['sector'],
                        'current_price': screening_data['current_price']
                    }
                    high_quality_signals.append(combined_signal)
        
        # Sort by combined score (screening + signal confidence)
        high_quality_signals.sort(
            key=lambda x: (x['screening_score'] + x['primary_confidence']) / 2,
            reverse=True
        )
        
        return {
            'run_date': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'screening_summary': screening_report['summary'],
            'signal_summary': signal_report['summary'],
            'high_quality_signals': high_quality_signals,
            'top_screened_stocks': screening_report['top_10_candidates'],
            'bullish_signals': signal_report['top_bullish_signals'],
            'bearish_signals': signal_report['top_bearish_signals'],
            'statistics': {
                'total_stocks_screened': len(screening_report['all_results']),
                'passed_screening': len([s for s in screening_report['all_results'] if s['overall_pass']]),
                'total_signals_generated': len(signal_report['all_results']),
                'high_confidence_signals': len(high_quality_signals),
                'bullish_signals': len(signal_report['top_bullish_signals']),
                'bearish_signals': len(signal_report['top_bearish_signals'])
            }
        }
    
    def _store_results(self, results: Dict):
        """
        Store results in database and local storage
        """
        try:
            # Store in memory
            self.results_storage.append(results)
            
            # Keep only last 30 days of results
            if len(self.results_storage) > 30:
                self.results_storage = self.results_storage[-30:]
            
            # Store in database if available
            if self.db_engine:
                self._store_in_database(results)
            
            # Store as JSON file
            self._store_as_json(results)
            
        except Exception as e:
            logger.error(f"Error storing results: {e}")
    
    def _store_in_database(self, results: Dict):
        """
        Store results in database
        """
        try:
            with self.db_engine.connect() as conn:
                # Create table if not exists
                conn.execute(text("""
                    CREATE TABLE IF NOT EXISTS daily_screening_results (
                        id SERIAL PRIMARY KEY,
                        run_date TIMESTAMP NOT NULL,
                        results JSONB NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """))
                
                # Insert results
                conn.execute(text("""
                    INSERT INTO daily_screening_results (run_date, results)
                    VALUES (:run_date, :results)
                """), {
                    'run_date': datetime.now(),
                    'results': json.dumps(results)
                })
                
                conn.commit()
                logger.info("Results stored in database")
                
        except Exception as e:
            logger.error(f"Error storing in database: {e}")
    
    def _store_as_json(self, results: Dict):
        """
        Store results as JSON file
        """
        try:
            os.makedirs('screening_results', exist_ok=True)
            
            filename = f"screening_results/daily_results_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
            
            with open(filename, 'w') as f:
                json.dump(results, f, indent=2, default=str)
            
            logger.info(f"Results stored as {filename}")
            
        except Exception as e:
            logger.error(f"Error storing as JSON: {e}")
    
    def _send_notifications(self, results: Dict):
        """
        Send notifications about screening results
        """
        try:
            # Generate notification message
            message = self._generate_notification_message(results)
            
            # Send email notification
            if self.config['notification_email'] and self.config['smtp_server']:
                self._send_email_notification(message, results)
            
            # Send Slack notification
            if self.config['slack_webhook']:
                self._send_slack_notification(message, results)
            
            # Send Discord notification
            if self.config['discord_webhook']:
                self._send_discord_notification(message, results)
                
        except Exception as e:
            logger.error(f"Error sending notifications: {e}")
    
    def _generate_notification_message(self, results: Dict) -> str:
        """
        Generate notification message
        """
        stats = results['statistics']
        high_quality = results['high_quality_signals'][:5]
        
        message = f"""
📊 Daily Stock Screening Report - {results['run_date']}

📈 SUMMARY:
• Total Stocks Screened: {stats['total_stocks_screened']}
• Passed Screening: {stats['passed_screening']}
• High-Quality Signals: {stats['high_confidence_signals']}
• Bullish Signals: {stats['bullish_signals']}
• Bearish Signals: {stats['bearish_signals']}

🎯 TOP HIGH-QUALITY SIGNALS:
"""
        
        for i, signal in enumerate(high_quality, 1):
            direction = "🟢" if signal['primary_signal'] == 'BULLISH' else "🔴"
            message += f"{i}. {direction} {signal['ticker']} - {signal['primary_confidence']}% confidence\n"
            message += f"   Price: ${signal['current_price']} | Score: {signal['screening_score']}/100\n"
            message += f"   Sector: {signal['sector']}\n"
            if signal['primary_reasons']:
                message += f"   Reason: {signal['primary_reasons'][0]}\n"
            message += "\n"
        
        return message
    
    def _send_email_notification(self, message: str, results: Dict):
        """
        Send email notification
        """
        try:
            msg = MIMEMultipart()
            msg['From'] = self.config['smtp_username']
            msg['To'] = self.config['notification_email']
            msg['Subject'] = f"Daily Stock Screening Report - {datetime.now().strftime('%Y-%m-%d')}"
            
            msg.attach(MIMEText(message, 'plain'))
            
            with smtplib.SMTP(self.config['smtp_server'], self.config['smtp_port']) as server:
                server.starttls()
                server.login(self.config['smtp_username'], self.config['smtp_password'])
                server.send_message(msg)
            
            logger.info("Email notification sent")
            
        except Exception as e:
            logger.error(f"Error sending email: {e}")
    
    def _send_slack_notification(self, message: str, results: Dict):
        """
        Send Slack notification
        """
        try:
            payload = {
                'text': message,
                'username': 'Stock Screener Bot',
                'icon_emoji': ':chart_with_upwards_trend:'
            }
            
            response = requests.post(self.config['slack_webhook'], json=payload)
            if response.status_code == 200:
                logger.info("Slack notification sent")
            else:
                logger.error(f"Slack notification failed: {response.status_code}")
                
        except Exception as e:
            logger.error(f"Error sending Slack notification: {e}")
    
    def _send_discord_notification(self, message: str, results: Dict):
        """
        Send Discord notification
        """
        try:
            payload = {
                'content': message,
                'username': 'Stock Screener Bot'
            }
            
            response = requests.post(self.config['discord_webhook'], json=payload)
            if response.status_code == 204:
                logger.info("Discord notification sent")
            else:
                logger.error(f"Discord notification failed: {response.status_code}")
                
        except Exception as e:
            logger.error(f"Error sending Discord notification: {e}")
    
    def _send_error_notification(self, error_message: str):
        """
        Send error notification
        """
        try:
            message = f"""
🚨 ERROR in Daily Stock Screening Pipeline

Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
Error: {error_message}

Please check the logs for more details.
"""
            
            if self.config['slack_webhook']:
                payload = {
                    'text': message,
                    'username': 'Stock Screener Bot',
                    'icon_emoji': ':warning:'
                }
                requests.post(self.config['slack_webhook'], json=payload)
            
            if self.config['notification_email'] and self.config['smtp_server']:
                msg = MIMEMultipart()
                msg['From'] = self.config['smtp_username']
                msg['To'] = self.config['notification_email']
                msg['Subject'] = "ERROR: Daily Stock Screening Pipeline"
                msg.attach(MIMEText(message, 'plain'))
                
                with smtplib.SMTP(self.config['smtp_server'], self.config['smtp_port']) as server:
                    server.starttls()
                    server.login(self.config['smtp_username'], self.config['smtp_password'])
                    server.send_message(msg)
                    
        except Exception as e:
            logger.error(f"Error sending error notification: {e}")
    
    def schedule_daily_runs(self):
        """
        Schedule daily screening runs
        """
        # Schedule for 7:00 AM EST (before market open)
        schedule.every().day.at("07:00").do(self.run_daily_screening)
        
        # Schedule for 6:00 PM EST (after market close)
        schedule.every().day.at("18:00").do(self.run_daily_screening)
        
        logger.info("Daily screening scheduled for 7:00 AM and 6:00 PM EST")
    
    def run_scheduler(self):
        """
        Run the scheduler (blocking)
        """
        self.schedule_daily_runs()
        
        logger.info("Scheduler started. Waiting for scheduled runs...")
        
        while True:
            schedule.run_pending()
            time.sleep(60)  # Check every minute
    
    def run_once(self):
        """
        Run screening once immediately
        """
        logger.info("Running screening once...")
        return self.run_daily_screening()
    
    def get_latest_results(self) -> Optional[Dict]:
        """
        Get the latest screening results
        """
        if self.results_storage:
            return self.results_storage[-1]
        return None
    
    def get_historical_results(self, days: int = 7) -> List[Dict]:
        """
        Get historical screening results
        """
        return self.results_storage[-days:] if self.results_storage else []


# Cloud Functions for GCP/AWS Lambda
def cloud_function_handler(event, context):
    """
    Cloud function handler for scheduled runs
    """
    try:
        scheduler = DailyScheduler()
        results = scheduler.run_once()
        
        return {
            'statusCode': 200,
            'body': json.dumps({
                'message': 'Daily screening completed successfully',
                'signals_found': len(results.get('high_quality_signals', [])),
                'timestamp': datetime.now().isoformat()
            })
        }
    except Exception as e:
        logger.error(f"Cloud function error: {e}")
        return {
            'statusCode': 500,
            'body': json.dumps({
                'error': str(e),
                'timestamp': datetime.now().isoformat()
            })
        }


def main():
    """
    Main function to run the scheduler
    """
    import argparse
    
    parser = argparse.ArgumentParser(description='Daily Stock Screening Scheduler')
    parser.add_argument('--run-once', action='store_true', help='Run screening once and exit')
    parser.add_argument('--schedule', action='store_true', help='Run scheduled screening')
    
    args = parser.parse_args()
    
    scheduler = DailyScheduler()
    
    if args.run_once:
        results = scheduler.run_once()
        print(f"Screening completed. Found {len(results.get('high_quality_signals', []))} high-quality signals.")
    elif args.schedule:
        scheduler.run_scheduler()
    else:
        print("Usage: python daily_scheduler.py [--run-once | --schedule]")
        print("  --run-once: Run screening once and exit")
        print("  --schedule: Run scheduled screening")


if __name__ == "__main__":
    main()