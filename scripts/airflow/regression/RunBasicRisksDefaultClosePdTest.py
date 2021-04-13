from eod_pd import basic_risks_default_close_pd_run
from regression.RegressionTestCase import RegressionTestCase
from regression.regression_tables import eod_basic_risks


# # 17.run pv & greeks for all positions
# basic_risks_default_close_pd_run(eod_end_date.date())
class RunBasicRisksDefaultClosePdTest(RegressionTestCase):
    def __init__(self, eod_end_date):
        self.eod_end_date = eod_end_date
        self.result_tables = [
            eod_basic_risks
        ]

    def test_run(self):
        basic_risks_default_close_pd_run(self.eod_end_date)
