from eod_pd import eod_spot_scenarios_by_market_default_close_pd_run
from regression.RegressionTestCase import RegressionTestCase
from regression.regression_tables import spot_scenarios_report


class RunEodSpotScenariosByMarketDefaultClosePdTest(RegressionTestCase):
    def __init__(self, current_date):
        self.current_date = current_date
        self.result_tables = [
            spot_scenarios_report
        ]

    def test_run(self):
        eod_spot_scenarios_by_market_default_close_pd_run(self.current_date)
