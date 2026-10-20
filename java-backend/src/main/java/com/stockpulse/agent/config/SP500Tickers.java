package com.stockpulse.agent.config;

import java.util.Arrays;
import java.util.List;

/**
 * S&P 500 Ticker List Configuration
 * Contains all S&P 500 component stocks for comprehensive market screening.
 */
public class SP500Tickers {

    public static final List<String> TICKERS = Arrays.asList(
        // Technology
        "AAPL", "MSFT", "GOOGL", "GOOG", "META", "NVDA", "AVGO", "ORCL", "CRM", "ADBE",
        "AMD", "CSCO", "ACN", "INTC", "IBM", "INTU", "NOW", "QCOM", "TXN", "AMAT",
        "MU", "ADI", "LRCX", "KLAC", "SNPS", "CDNS", "MCHP", "HPQ", "HPE", "FTNT",
        "PANW", "CRWD", "ZS", "KEYS", "ANSS", "MPWR", "ON", "SWKS", "AKAM", "FFIV",
        "JNPR", "NTAP", "WDC", "GEN", "CTSH", "IT", "EPAM", "LDOS", "TRMB", "PTC",

        // Communication Services
        "NFLX", "DIS", "CMCSA", "VZ", "T", "TMUS", "CHTR", "EA", "TTWO", "WBD",
        "PARA", "FOX", "FOXA", "NWS", "NWSA", "OMC", "IPG", "LYV", "MTCH", "DISH",

        // Consumer Discretionary
        "AMZN", "TSLA", "HD", "MCD", "NKE", "SBUX", "LOW", "TJX", "BKNG", "CMG",
        "MAR", "ORLY", "AZO", "ROST", "DHI", "LEN", "PHM", "GPC", "ULTA", "BBY",
        "DRI", "YUM", "HLT", "WYNN", "MGM", "CCL", "RCL", "NCLH", "EXPE", "GRMN",
        "EBAY", "ETSY", "APTV", "GM", "F", "TSCO", "DG", "DLTR", "KMX", "BWA",
        "POOL", "LKQ", "NVR", "TPR", "RL", "HAS", "PVH", "VFC", "WHR",

        // Consumer Staples
        "PG", "KO", "PEP", "COST", "WMT", "PM", "MO", "MDLZ", "CL", "EL",
        "GIS", "KHC", "SYY", "HSY", "K", "KDP", "STZ", "ADM", "MKC", "CLX",
        "CAG", "HRL", "SJM", "TSN", "CPB", "CHD", "KR", "WBA", "TAP", "BG",

        // Energy
        "XOM", "CVX", "COP", "EOG", "SLB", "MPC", "PSX", "VLO", "PXD", "OXY",
        "DVN", "HES", "HAL", "BKR", "FANG", "CTRA", "MRO", "APA", "OVV", "TRGP",

        // Financials
        "BRK.B", "JPM", "BAC", "WFC", "MS", "GS", "SCHW", "BLK", "C", "AXP",
        "SPGI", "CME", "ICE", "CB", "MMC", "PGR", "AON", "AIG", "MET", "PRU",
        "TRV", "ALL", "AFL", "PNC", "USB", "TFC", "COF", "BK", "STT", "FITB",
        "HBAN", "RF", "CFG", "KEY", "NTRS", "MTB", "DFS", "SYF", "AMP", "IVZ",
        "BEN", "LNC", "CBOE", "NDAQ", "MSCI", "MCO", "FDS", "MKTX", "CINF", "WRB",
        "GL", "RJF", "ZION", "L", "RE", "AIZ",

        // Healthcare
        "UNH", "JNJ", "LLY", "PFE", "ABBV", "MRK", "TMO", "ABT", "DHR", "BMY",
        "AMGN", "GILD", "CVS", "ELV", "ISRG", "MDT", "CI", "SYK", "REGN", "VRTX",
        "ZTS", "BDX", "BSX", "MCK", "HCA", "HUM", "IDXX", "IQV", "EW", "MTD",
        "A", "DXCM", "RMD", "ALGN", "ILMN", "HOLX", "TFX", "TECH", "WAT", "RVTY",
        "BAX", "ZBH", "VTRS", "CAH", "COR", "PODD", "INCY", "BIIB", "MRNA", "MOH",

        // Industrials
        "UNP", "HON", "UPS", "CAT", "RTX", "BA", "DE", "LMT", "GE", "GD",
        "NOC", "MMM", "FDX", "CSX", "NSC", "ETN", "ITW", "EMR", "PH", "JCI",
        "ROK", "CARR", "OTIS", "TT", "CMI", "PCAR", "FAST", "WM", "RSG", "VRSK",
        "AME", "CTAS", "PAYX", "CPRT", "ODFL", "GWW", "J", "IR", "DOV", "XYL",
        "SWK", "HWM", "AXON", "HUBB", "TDG", "WAB", "LHX", "IEX", "PNR", "ALLE",
        "NDSN", "FTV", "BR", "ROP", "URI", "CHRW", "EFX", "PWR", "GNRC", "JBHT",
        "DAL", "LUV", "AAL", "UAL", "EXPD",

        // Materials
        "LIN", "APD", "SHW", "ECL", "FCX", "NEM", "DOW", "NUE", "PPG", "CTVA",
        "DD", "VMC", "MLM", "IFF", "ALB", "CF", "MOS", "FMC", "CE", "BALL",
        "PKG", "AVY", "SEE", "IP", "WRK", "EMN", "AMCR",

        // Real Estate
        "PLD", "AMT", "EQIX", "CCI", "PSA", "SPG", "WELL", "O", "DLR", "VICI",
        "AVB", "EQR", "VTR", "ARE", "SBAC", "MAA", "UDR", "ESS", "WY", "EXR",
        "CBRE", "IRM", "INVH", "KIM", "CPT", "REG", "BXP", "HST", "PEAK", "FRT",

        // Utilities
        "NEE", "DUK", "SO", "D", "SRE", "AEP", "EXC", "XEL", "PCG", "WEC",
        "ED", "EIX", "AWK", "ES", "DTE", "PPL", "ETR", "FE", "AEE", "CMS",
        "CNP", "ATO", "EVRG", "NI", "LNT", "PNW", "NRG",

        // Additional popular stocks often tracked
        "UBER", "PYPL", "SQ", "ROKU", "ZM", "PLTR", "SNOW", "DDOG", "NET", "TEAM",
        "WDAY", "SPLK", "OKTA", "DOCU", "TWLO", "MDB", "COIN", "PATH", "BILL", "HUBS"
    );

    public static List<String> getTickers() {
        return TICKERS;
    }

    public static int getCount() {
        return TICKERS.size();
    }
}
