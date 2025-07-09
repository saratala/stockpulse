"""
Advanced AI stock prediction models for StockPulse.
Implements XGBoost, LSTM, and FB Prophet models.
"""
import pandas as pd
import numpy as np

# XGBoost
try:
    import xgboost as xgb
except ImportError:
    xgb = None

# LSTM (Keras)
try:
    from tensorflow.keras.models import Sequential
    from tensorflow.keras.layers import LSTM, Dense, Dropout
    from tensorflow.keras.callbacks import EarlyStopping
    from sklearn.preprocessing import MinMaxScaler
except ImportError:
    Sequential = LSTM = Dense = Dropout = EarlyStopping = MinMaxScaler = None

# Prophet
try:
    from prophet import Prophet
except ImportError:
    Prophet = None

# ----------------------
# XGBoost Model
# ----------------------
def train_xgboost(X, y):
    if xgb is None:
        raise ImportError("xgboost is not installed.")
    model = xgb.XGBClassifier(n_estimators=100, max_depth=5, learning_rate=0.1, random_state=42)
    model.fit(X, y)
    return model

def predict_xgboost(model, X):
    proba = model.predict_proba(X)[:, 1]
    pred = (proba >= 0.5).astype(int)
    return pred, proba

# ----------------------
# LSTM Model (for time series)
# ----------------------
def create_lstm_model(input_shape):
    if Sequential is None:
        raise ImportError("TensorFlow/Keras is not installed.")
    model = Sequential()
    model.add(LSTM(50, return_sequences=True, input_shape=input_shape))
    model.add(Dropout(0.2))
    model.add(LSTM(50))
    model.add(Dropout(0.2))
    model.add(Dense(1, activation='sigmoid'))
    model.compile(optimizer='adam', loss='binary_crossentropy', metrics=['accuracy'])
    return model

def train_lstm(X, y, epochs=20, batch_size=32):
    if MinMaxScaler is None:
        raise ImportError("TensorFlow/Keras is not installed.")
    # Use all columns in X as features (all technicals)
    scaler = MinMaxScaler()
    X_scaled = scaler.fit_transform(X)
    # LSTM expects 3D input: (samples, timesteps, features)
    # We'll use a window of timesteps (e.g., 10 days)
    window = 10
    X_seq = []
    y_seq = []
    for i in range(len(X_scaled) - window):
        X_seq.append(X_scaled[i:i+window])
        y_seq.append(y[i+window])
    X_seq = np.array(X_seq)
    y_seq = np.array(y_seq)
    model = create_lstm_model((X_seq.shape[1], X_seq.shape[2]))
    early_stop = EarlyStopping(monitor='loss', patience=3)
    model.fit(X_seq, y_seq, epochs=epochs, batch_size=batch_size, verbose=0, callbacks=[early_stop])
    return model, scaler

def predict_lstm(model, scaler, X):
    X_scaled = scaler.transform(X)
    window = 10
    X_seq = []
    for i in range(len(X_scaled) - window):
        X_seq.append(X_scaled[i:i+window])
    X_seq = np.array(X_seq)
    proba = model.predict(X_seq).flatten()
    pred = (proba >= 0.5).astype(int)
    return pred, proba

# ----------------------
# Prophet Model (for regression/forecasting)
# ----------------------
def train_prophet(df, date_col='date', target_col='close'):
    if Prophet is None:
        raise ImportError("prophet is not installed.")
    prophet_df = df[[date_col, target_col]].rename(columns={date_col: 'ds', target_col: 'y'})
    model = Prophet()
    model.fit(prophet_df)
    return model

def predict_prophet(model, future_dates):
    forecast = model.predict(future_dates)
    return forecast[['ds', 'yhat', 'yhat_lower', 'yhat_upper']]

# ----------------------
# Model selection utility
# ----------------------
def get_model(name):
    if name == 'xgboost':
        return train_xgboost, predict_xgboost
    elif name == 'lstm':
        return train_lstm, predict_lstm
    elif name == 'prophet':
        return train_prophet, predict_prophet
    else:
        raise ValueError(f"Unknown model: {name}")
