from eod_pd import eod_counter_party_market_risk_by_underlyer_default_close_pd_run
from regression.RegressionTestCase import RegressionTestCase
from regression.regression_tables import counter_party_market_risk_by_underlyer_report


# # 20. 交易对手分品种风险报告
# eod_counter_party_market_risk_by_underlyer_default_close_pd_run(current_date)
class RunEodCounterPartyMarketRiskByUnderlyerDefaultClosePdTest(RegressionTestCase):
    def __init__(self, current_date):
        self.current_date = current_date
        self.result_tables = [
            counter_party_market_risk_by_underlyer_report
        ]

    def test_run(self):
        eod_counter_party_market_risk_by_underlyer_default_close_pd_run(self.current_date)
