from datetime import date
from eod_pd import eod_market_risk_by_book_underlyer_default_close_pd_run
from regression.RegressionTestCase import RegressionTestCase
from regression.regression_tables import market_risk_by_sub_underlyer_report


# # 19. 各子公司分品种风险
# eod_market_risk_by_book_underlyer_default_close_pd_run(current_date)
class RunEodMarketRiskByBookUnderlyerDefaultClosePdTest(RegressionTestCase):
    def __init__(self, current_date):
        self.current_date = current_date
        self.result_tables = [
            market_risk_by_sub_underlyer_report
        ]

    def test_run(self):
        eod_market_risk_by_book_underlyer_default_close_pd_run(self.current_date)
