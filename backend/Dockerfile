FROM python:3.10

WORKDIR /app
COPY requirements.txt .
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        build-essential \
        libc6-dev \
        ca-certificates \
        libatlas-base-dev \
        liblapack-dev \
        libblas-dev \
        python3-dev \
        libffi-dev \
        libssl-dev \
        git \
        cmake && \
    update-ca-certificates && \
    apt-get remove --purge -y git cmake python3-dev && \
    apt-get autoremove -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install Python dependencies
RUN pip install --upgrade pip \
    && pip install --no-cache-dir -r requirements.txt \
    && pip install xgboost prophet tensorflow
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
RUN echo '#!/bin/bash\necho "Starting ETL and ML pipeline..."\npython etl_finance.py &\npython sentiment_vader.py &\npython predict_engine.py &\npython predict_daily.py &\necho "Starting FastAPI server..."\nuvicorn main:app --host 0.0.0.0 --port 8000 &\necho "Starting Dashboard..."\npython dashboard.py\n' > /app/start.sh && \
    chmod +x /app/start.sh

# Add healthcheck for both services
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD /app/healthcheck.sh

EXPOSE 8000 8050
CMD ["./start.sh"]
# CMD ["python", "dashboard.py"]
# CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]

