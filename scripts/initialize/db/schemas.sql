CREATE SCHEMA IF NOT EXISTS auth_service;
ALTER SCHEMA auth_service OWNER TO bct;
CREATE SCHEMA IF NOT EXISTS model_service; 
ALTER SCHEMA model_service OWNER TO bct;
CREATE SCHEMA IF NOT EXISTS pricing_service; 
ALTER SCHEMA pricing_service OWNER TO bct;
CREATE SCHEMA IF NOT EXISTS market_data_service; 
ALTER SCHEMA market_data_service OWNER TO bct;
CREATE SCHEMA IF NOT EXISTS reference_data_service;
ALTER SCHEMA reference_data_service OWNER TO bct;
CREATE SCHEMA IF NOT EXISTS user_preference_service;
ALTER SCHEMA user_preference_service OWNER TO bct;
CREATE SCHEMA IF NOT EXISTS mock_trade_service;
ALTER SCHEMA mock_trade_service OWNER TO bct;
CREATE SCHEMA IF NOT EXISTS trade_service;
ALTER SCHEMA trade_service OWNER TO bct;
CREATE SCHEMA IF NOT EXISTS trade_snapshot_model;
ALTER SCHEMA trade_snapshot_model OWNER TO bct;
CREATE SCHEMA IF NOT EXISTS exchange_service;
ALTER SCHEMA exchange_service OWNER TO bct;
CREATE SCHEMA IF NOT EXISTS client_service;
ALTER SCHEMA client_service OWNER TO bct;
CREATE SCHEMA IF NOT EXISTS report_service;
ALTER SCHEMA report_service OWNER TO bct;
CREATE SCHEMA IF NOT EXISTS document_service;
ALTER SCHEMA document_service OWNER TO bct;
CREATE SCHEMA IF NOT EXISTS margin_service;
ALTER SCHEMA margin_service OWNER TO bct;
CREATE SCHEMA IF NOT EXISTS workflow_service;
ALTER SCHEMA workflow_service OWNER TO bct;
CREATE SCHEMA IF NOT EXISTS risk_control_service;
ALTER SCHEMA risk_control_service OWNER TO bct;