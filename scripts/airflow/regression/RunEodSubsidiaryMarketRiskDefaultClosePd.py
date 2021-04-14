from eod_pd import eod_subsidiary_market_risk_default_close_pd_run
from regression.RegressionTestCase import RegressionTestCase
from regression.regression_tables import subsidiary_market_risk_report


# # 22. 各子公司整体风险报告
# eod_subsidiary_market_risk_default_close_pd_run(current_date)
class RunEodSubsidiaryMarketRiskDefaultClosePd(RegressionTestCase):
    def __init__(self, current_date):
        self.current_date = current_date
        self.result_tables = [
            subsidiary_market_risk_report
        ]

    def test_run(self):
        eod_subsidiary_market_risk_default_close_pd_run(self.current_date)
