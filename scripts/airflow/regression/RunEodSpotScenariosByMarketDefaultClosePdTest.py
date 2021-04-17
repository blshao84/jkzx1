from ast import literal_eval

import csvdiff
from csvdiff import patch

from eod_pd import eod_spot_scenarios_by_market_default_close_pd_run
from regression.RegressionTestCase import RegressionTestCase, assert_results
from regression.regression_tables import spot_scenarios_report


class RunEodSpotScenariosByMarketDefaultClosePdTest(RegressionTestCase):
    def __init__(self, current_date):
        self.current_date = current_date
        self.result_tables = [
            spot_scenarios_report
        ]

    def test_run(self):
        eod_spot_scenarios_by_market_default_close_pd_run(self.current_date)

    def run(self, dump: bool, dry_run=False):
        self.test_run()
        if not dry_run:
            if dump:
                self.dump_result()
            else:
                bas = self.bas_result()
                reg = self.reg_result()
                keys_map = self.table_keys()
                for bas_name in bas:
                    bas_values = bas[bas_name]
                    keys = keys_map[bas_name]
                    reg_values = reg[bas_name]
                    diff = csvdiff.diff_records(list(map(self.convert_scenarios, bas_values)),
                                                list(map(self.convert_scenarios, reg_values)), keys)
                    assert_results(diff, bas_name)
                    diff = patch.create(bas_values, reg_values, keys, ignore_columns=['scenarios'])
                    assert_results(diff, bas_name)

    def convert_scenarios(self, value):
        value_dict = {}
        for key in self.table_keys()['report_service.spot_scenarios_report']:
            value_dict[key] = value.get(key)

        scenarios = literal_eval(value.get('scenarios'))
        if isinstance(scenarios, list):
            value_dict.update(scenarios[0])
        else:
            value_dict.update(scenarios)

        roundings = {}
        for k, v in spot_scenarios_report.roundings.items():
            if k.startswith('scenarios.'):
                roundings[k.replace('scenarios.', '')] = v
        for k, v in roundings.items():
            self.dfs_rounding(value_dict, k.split('.'), 0, v)
        return value_dict
