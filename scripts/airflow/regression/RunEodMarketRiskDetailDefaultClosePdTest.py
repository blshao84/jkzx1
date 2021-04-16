from eod_pd import eod_market_risk_detail_default_close_pd_run
from regression.RegressionTestCase import RegressionTestCase
from regression.regression_tables import market_risk_detail_report


# # 24. 全市场分品种风险报告
# eod_market_risk_detail_default_close_pd_run(current_date)
class RunEodMarketRiskDetailDefaultClosePdTest(RegressionTestCase):
    def __init__(self, current_date):
        self.current_date = current_date
        self.result_tables = [
            market_risk_detail_report
        ]

    def test_run(self):
        eod_market_risk_detail_default_close_pd_run(self.current_date)
