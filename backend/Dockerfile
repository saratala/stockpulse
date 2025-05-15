FROM python:3.10

WORKDIR /app
COPY requirements.txt .
RUN apt-get update && apt-get install -y ca-certificates && update-ca-certificates && rm -rf /var/lib/apt/lists/*
RUN pip install --no-cache-dir -r requirements.txt
RUN python -m textblob.download_corpora
RUN python -c "import nltk; nltk.download('vader_lexicon')"

COPY . .

# Create healthcheck script
COPY <<EOF /app/healthcheck.sh
#!/bin/bash
curl -f http://localhost:8050/_dash-layout && \
curl -f http://localhost:8000/health || exit 1
EOF


# Create start script
RUN echo '#!/bin/bash\n\
echo "Starting ETL and ML pipeline..."\n\
python etl_finance.py &\n\
python sentiment_vader.py &\n\
python predict_engine.py &\n\
python predict_daily.py &\n\
echo "Starting FastAPI server..."\n\
uvicorn main:app --host 0.0.0.0 --port 8000 &\n\
echo "Starting Dashboard..."\n\
python dashboard.py\n' > /app/start.sh && \
    chmod +x /app/start.sh

# Add healthcheck for both services
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD /app/healthcheck.sh


EXPOSE 8000 8050
CMD ["./start.sh"]
# CMD ["python", "dashboard.py"]
# CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]

