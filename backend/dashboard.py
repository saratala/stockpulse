import os
import pandas as pd
from datetime import date
from dotenv import load_dotenv
import dash
from dash import dcc, html, dash_table, Input, Output, no_update
import dash_bootstrap_components as dbc
import plotly.express as px
from db import get_engine_with_retry
# Load environment variables

# Verify Dash version
print(f"Dash version: {dash.__version__}")

load_dotenv()

# Connect to DB
engine = get_engine_with_retry()

# Dash app setup
# Dash app setup with server configuration
app = dash.Dash(
    __name__, 
    external_stylesheets=[dbc.themes.BOOTSTRAP],
    suppress_callback_exceptions=True
    # Add server configuration
    #server_url="http://127.0.0.1:8050"
    #assets_folder='assets'
)
server = app.server
app.title = "Stock Movement Prediction Dashboard"

def fetch_predictions(selected_date):
    """Fetch all predictions with an optional date filter"""
    try:
        if selected_date:
            query = f"""
                SELECT 
                    ticker,
                    prediction_date,
                    target_date,
                    predicted_movement_percent,
                    predicted_direction,
                    confidence_score,
                    model_version
                FROM predictions
                WHERE DATE(prediction_date) = '{selected_date}'
                ORDER BY predicted_movement_percent DESC
            """
        else:
            query = """
                SELECT 
                    ticker,
                    prediction_date,
                    target_date,
                    predicted_movement_percent,
                    predicted_direction,
                    confidence_score,
                    model_version
                FROM predictions
                ORDER BY prediction_date DESC, predicted_movement_percent DESC
                LIMIT 100
            """
        return pd.read_sql(query, engine)
    except Exception as e:
        print(f"Error fetching predictions: {e}")
        return pd.DataFrame()

# Update layout to show more data
app.layout = dbc.Container([
    html.H1("Stock Movement Predictions Dashboard", className="my-4"),
    
    dbc.Row([
        dbc.Col([
            dcc.DatePickerSingle(
                id='date-picker',
                date=date.today(),
                className="mb-4"
            )
        ])
    ]),

    dbc.Row([
        dbc.Col([
            dash_table.DataTable(
                id='prediction-table',
                columns=[
                    {"name": "Ticker", "id": "ticker"},
                    {"name": "Prediction Date", "id": "prediction_date"},
                    {"name": "Target Date", "id": "target_date"},
                    {"name": "Movement %", "id": "predicted_movement_percent"},
                    {"name": "Direction", "id": "predicted_direction"},
                    {"name": "Confidence", "id": "confidence_score"},
                    {"name": "Model", "id": "model_version"}
                ],
                style_table={'overflowX': 'auto'},
                style_cell={
                    'textAlign': 'left',
                    'padding': '10px',
                    'minWidth': '100px'
                },
                style_header={
                    'backgroundColor': 'rgb(230, 230, 230)',
                    'fontWeight': 'bold'
                },
                page_size=20
            )
        ], width=12)
    ]),

    dbc.Row([
        dbc.Col([
            dcc.Graph(id='probability-chart')
        ], width=12)
    ])
], fluid=True)

@app.callback(
    Output('prediction-table', 'data'),
    Output('probability-chart', 'figure'),
    Input('date-picker', 'date')
)
def update_dashboard(selected_date):
    df = fetch_predictions(selected_date)
    
    if df.empty:
        return [], {}
    
    # Convert numeric columns
    df['predicted_movement_percent'] = df['predicted_movement_percent'].astype(float)
    df['confidence_score'] = df['confidence_score'].astype(float)

    # Create bar chart
    fig = px.bar(
        df.head(20),  # Show top 20 predictions
        x='ticker',
        y='predicted_movement_percent',
        color='predicted_direction',
        title='Top 20 Predicted Stock Movements',
        labels={'predicted_movement_percent': 'Probability %'},
        hover_data=['confidence_score'],
        height=500
    )

    fig.update_layout(
        xaxis_title="Stock Ticker",
        yaxis_title="Predicted Movement Probability (%)",
        showlegend=True
    )

    return df.to_dict('records'), fig

if __name__ == "__main__":
    app.run(
        debug=True,
        host="0.0.0.0",
        port=8050
    )