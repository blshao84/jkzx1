from regression.RegressionTestCase import RegressionTestCase
from regression.regression_tables import *
from eod_pd import eod_position_default_close_pd_run


class RunEodPositionDefaultClosePdTest(RegressionTestCase):
    def __init__(self, current_date):
        self.current_date = current_date
        self.result_tables = [bct_otc_custom_postions]


    def test_run(self):
        eod_position_default_close_pd_run(self.current_date)
