from regression.RegressionTestCase import RegressionTestCase
from regression.regression_tables import counter_party_market_risk_report
from eod_pd import eod_counter_party_market_risk_default_close_pd_run as cpty_market_risk_run


class RunEodCounterpartyMarketRiskDefaultCloseDdTest(RegressionTestCase):
    # 21. 交易对手风险报告

    def __init__(self, current_date):
        self.current_date = current_date
        self.result_tables = [counter_party_market_risk_report]

    def test_run(self):
        cpty_market_risk_run(self.current_date)
