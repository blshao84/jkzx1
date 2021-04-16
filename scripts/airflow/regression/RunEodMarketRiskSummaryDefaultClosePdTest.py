from regression.RegressionTestCase import RegressionTestCase
from regression.regression_tables import market_risk_report
from eod_pd import eod_market_risk_summary_default_close_pd_run as market_risk_summary_run


class RunEodMarketRiskSummaryDefaultClosePdTest(RegressionTestCase):

    def __init__(self, current_date):
        self.current_date = current_date
        self.result_tables = [market_risk_report]

    def test_run(self):
        market_risk_summary_run(self.current_date)
