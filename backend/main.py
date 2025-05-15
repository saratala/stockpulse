from fastapi import FastAPI
import logging

app = FastAPI()
logger = logging.getLogger(__name__)

@app.get("/")
def read_root():
    return {"message": "Backend is working!"}

@app.get("/health")
async def health_check():
    return {"status": "healthy"}