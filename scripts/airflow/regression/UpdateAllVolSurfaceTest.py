from ast import literal_eval

import csvdiff
from csvdiff import patch
from pandas import DataFrame
from tabulate import tabulate

from dags.service.vol_surface_service import VolSurfaceService
from regression.RegressionTestCase import RegressionTestCase, assert_results
from regression.regression_tables import vol_surface


# # 12. calc implied vol
# VolSurfaceService.update_all_vol_surface(eod_end_date.date(), eod_end_date.date(), 4)
class UpdateAllVolSurfaceTest(RegressionTestCase):
    def __init__(self, eod_start_date, eod_end_date):
        self.eod_start_date = eod_start_date
        self.eod_end_date = eod_end_date
        self.result_tables = [
            vol_surface
        ]

    def test_run(self):
        VolSurfaceService.update_all_vol_surface(self.eod_start_date, self.eod_end_date, process_num=4)

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
                    diff = csvdiff.diff_records(list(map(self.convert_model_info, bas_values)),
                                                list(map(self.convert_model_info, reg_values)), keys)
                    assert_results(diff, bas_name)
                    diff = patch.create(bas_values, reg_values, keys, ignore_columns=['model_info'])
                    assert_results(diff, bas_name)

    def convert_model_info(self, value):
        value_dict = {}
        for key in self.table_keys()['market_data.vol_surface']:
            value_dict[key] = value.get(key)

        model_info = literal_eval(value.get('model_info'))
        if isinstance(model_info, list):
            value_dict.update(model_info[0])
        else:
            value_dict.update(model_info)

        roundings = {}
        for k, v in vol_surface.roundings.items():
            if k.startswith('model_info.'):
                roundings[k.replace('model_info.', '')] = v
        for k, v in roundings.items():
            self.dfs_rounding(value_dict, k.split('.'), 0, v)
        return value_dict
