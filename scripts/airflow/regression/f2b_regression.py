import warnings

from pandas.core.common import SettingWithCopyWarning

from dags.service.future_contract_info_service import FutureContractInfoService
from dags.service.realized_vol_service import RealizedVolService
from eod_pd import basic_otc_company_type_run, basic_cash_flow_pd_run, basic_cash_flow_today_pd_run, \
    basic_underlyer_position_default_close_pd_run, basic_instrument_contract_type_run, basic_position_pd_run, \
    basic_risks_default_close_pd_run, eod_position_default_close_pd_run, \
    eod_market_risk_by_book_underlyer_default_close_pd_run, \
    eod_counter_party_market_risk_by_underlyer_default_close_pd_run, eod_counter_party_market_risk_default_close_pd_run, \
    eod_subsidiary_market_risk_default_close_pd_run, eod_market_risk_summary_default_close_pd_run, \
    eod_market_risk_detail_default_close_pd_run
from market_data.eod_market_data import *
from regression.CacheInstrumentTypeTest import CacheInstrumentTypeTest
from regression.CacheOtcPositionTest import CacheOtcPositionTest
from regression.ImportBCTCalendarTest import ImportBCTCalendarTest
from regression.ImportBCTTradeTest import ImportBCTTradeTest
from regression.CacheCompanyTest import CacheCompanyTest
from regression.ImportTerminalCalendarTest import ImportTerminalCalendarTest
from regression.ImportTerminalMarketDataTest import ImportTerminalMarketDataTest
from regression.ImportTerminalTradeTest import ImportTerminalTradeTest
from regression.RunBasicRisksDefaultClosePdTest import RunBasicRisksDefaultClosePdTest
from regression.RunEodCounterPartyMarketRiskByUnderlyerDefaultClosePd import \
    RunEodCounterPartyMarketRiskByUnderlyerDefaultClosePd
from regression.RunEodMarketRiskByBookUnderlyerDefaultClosePdTest import \
    RunEodMarketRiskByBookUnderlyerDefaultClosePdTest
from regression.RunEodSubsidiaryMarketRiskDefaultClosePd import RunEodSubsidiaryMarketRiskDefaultClosePd
from regression.SyncTerminalInstrumentTest import SyncTerminalInstrumentTest
from regression.UpdateAllVolSurfaceTest import UpdateAllVolSurfaceTest
from regression.UpdateBCTInstrumentTest import UpdateBCTInstrumentTest
from regression.UpdateBCTQuoteTest import UpdateBCTQuoteTest
from regression.UpdateCashflowTest import UpdateCashflowTest
from regression.UpdateDaysInstrumentRealizedVolTest import UpdateDaysInstrumentRealizedVolTest
from regression.UpdateEodOtcFutureContractTest import UpdateEodOtcFutureContractTest
from regression.RunEodPositionDefaultClosePdTest import RunEodPositionDefaultClosePdTest
from regression.UpdateImpliedVolTest import UpdateImpliedVolTest
from regression.RunEodCounterpartyMarketRiskDefaultCloseDdTest import \
    RunEodCounterpartyMarketRiskDefaultCloseDdTest
from regression.RunEodMarketRiskSummaryDefaultClosePdTest import \
    RunEodMarketRiskSummaryDefaultClosePdTest

from terminal.service import VolSurfaceService
from trade_import.trade_import_fuc import trade_data_import


# 第一次调用data-service的时候zuul总会失败，通过warmup来workaround这个问题
def warm_up():
    try:
        bct_token = login_token(user, password, host)
        fetch_instrument_info(host, bct_token)
        get_terminal_instruments_list(bct_token)
        # TODO: 这个任务对于监控中心目前test来说是没有数据的，但可以考虑mock数据后将其纳入到测试中
        # 14. fetch listed positions (no need for now)
        basic_underlyer_position_default_close_pd_run()
    except Exception as e:
        logging.warning(str(e))


if __name__ == '__main__':
    warnings.simplefilter(action='ignore', category=FutureWarning)
    warnings.simplefilter(action='ignore', category=SettingWithCopyWarning)
    current_date = '2020-08-27'
    eod_end_date = datetime.strptime(current_date, '%Y-%m-%d')
    eod_start_date = eod_end_date - timedelta(days=1)
    dump = False
    # dump = True
    warm_up()
    test_suite = [
        ImportBCTCalendarTest(),  # 1
        ImportTerminalCalendarTest(),  # 2
        ImportTerminalMarketDataTest(eod_start_date, eod_end_date),  # 3
        UpdateBCTInstrumentTest(),  # 4
        SyncTerminalInstrumentTest(),  # 5
        UpdateBCTQuoteTest(current_date),  # 6
        ImportBCTTradeTest(current_date),  # 7
        ImportTerminalTradeTest(eod_start_date, eod_end_date),  # 8
        UpdateImpliedVolTest(eod_start_date, eod_end_date),  # 9
        UpdateEodOtcFutureContractTest(eod_start_date, eod_end_date),  # 10
        UpdateDaysInstrumentRealizedVolTest(eod_start_date.date(), eod_end_date.date()),  # 11
        # UpdateAllVolSurfaceTest(eod_start_date.date(), eod_end_date.date()),  # 12 todo: diff
        CacheCompanyTest(),  # 13.1
        UpdateCashflowTest(),  # 13.2
        CacheInstrumentTypeTest(),  # 15
        CacheOtcPositionTest(eod_end_date),  # 16
        RunBasicRisksDefaultClosePdTest(eod_end_date.date()),  # 17
        RunEodPositionDefaultClosePdTest(current_date),  # 18. merge position and risk
        RunEodMarketRiskByBookUnderlyerDefaultClosePdTest(current_date),  # 19
        RunEodCounterPartyMarketRiskByUnderlyerDefaultClosePd(current_date),  # 20. 交易对手分品种风险报告
        RunEodCounterpartyMarketRiskDefaultCloseDdTest(current_date),  # 21 交易对手风险报告
        RunEodSubsidiaryMarketRiskDefaultClosePd(current_date),  # 22. 各子公司整体风险报告
        RunEodMarketRiskSummaryDefaultClosePdTest(current_date)  # 23. 全市场整体风险汇总报告
    ]
    for test_case in test_suite:
        print(type(test_case))
        test_case.run(dump)
    # # 23. 全市场整体风险汇总报告
    # eod_market_risk_summary_default_close_pd_run(current_date)
    # # 24. 全市场分品种风险报告
    # eod_market_risk_detail_default_close_pd_run(current_date)
