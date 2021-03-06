import dags.dbo.db_model as std
from datetime import datetime, date
from terminal.dao import QuoteCloseRepo
from terminal.dbo import Instrument, QuoteClose
from terminal.utils.datetime_utils import DateTimeUtils
from dags.utils.dataapi_utils import Client
from dags.service.base_backfiller_service import BaseBackfillerService
import dags.utils.model_converter as converter_utils
from terminal.utils import Logging
import json
from terminal.dto import InstrumentType, AssetClassType
from dags.dto import BreakType, ReconFromType
from dags.dao import QuoteCloseBackfillerRepo

logging = Logging.getLogger(__name__)


class QuoteCloseBackfillerService(BaseBackfillerService):
    @staticmethod
    def get_recon_breaks(db_session, start_date, end_date, instrument=None,
                         recon_from_type=ReconFromType.QUOTE_CLOSE_RECON):
        return QuoteCloseBackfillerRepo.get_quote_close_recon_breaks(
            db_session, start_date, end_date, instrument, recon_from_type)

    @staticmethod
    def calc_one_quote_close(instrument, close_data):
        try:
            if instrument.instrumentType == InstrumentType.STOCK.name:
                std_quote_close = QuoteClose(instrumentId=instrument.instrumentId,
                                             tradeDate=close_data.get('tradeDate'),
                                             openPrice=close_data.get('openPrice'),
                                             closePrice=close_data.get('closePrice'),
                                             highPrice=close_data.get('highestPrice'),
                                             lowPrice=close_data.get('lowestPrice'),
                                             settlePrice=None,
                                             volume=close_data.get('turnoverVol'),
                                             amount=close_data.get('turnoverValue'),
                                             preClosePrice=close_data.get('preClosePrice'),
                                             returnRate=None,
                                             quoteTime=None)

            elif instrument.instrumentType == InstrumentType.FUND.name:
                std_quote_close = QuoteClose(instrumentId=instrument.instrumentId,
                                             tradeDate=close_data.get('tradeDate'),
                                             openPrice=close_data.get('openPrice'),
                                             closePrice=close_data.get('closePrice'),
                                             highPrice=close_data.get('highestPrice'),
                                             lowPrice=close_data.get('lowestPrice'),
                                             settlePrice=None,
                                             volume=close_data.get('turnoverVol'),
                                             amount=close_data.get('turnoverValue'),
                                             preClosePrice=close_data.get('preClosePrice'),
                                             returnRate=None,
                                             quoteTime=None)

            elif instrument.instrumentType == InstrumentType.INDEX.name:
                std_quote_close = QuoteClose(instrumentId=instrument.instrumentId,
                                             tradeDate=close_data.get('tradeDate'),
                                             openPrice=close_data.get('openIndex'),
                                             closePrice=close_data.get('closeIndex'),
                                             highPrice=close_data.get('highestIndex'),
                                             lowPrice=close_data.get('lowestIndex'),
                                             settlePrice=None,
                                             volume=close_data.get('turnoverVol'),
                                             amount=close_data.get('turnoverValue'),
                                             preClosePrice=close_data.get('preCloseIndex'),
                                             returnRate=None,
                                             quoteTime=None)
            # ????????????EQUITY / COMMODITY
            elif instrument.instrumentType == InstrumentType.FUTURE.name:
                std_quote_close = QuoteClose(instrumentId=instrument.instrumentId,
                                             tradeDate=close_data.get('tradeDate'),
                                             openPrice=close_data.get('openPrice'),
                                             closePrice=close_data.get('closePrice'),
                                             highPrice=close_data.get('highestPrice'),
                                             lowPrice=close_data.get('lowestPrice'),
                                             settlePrice=close_data.get('settlePrice'),
                                             volume=close_data.get('turnoverVol'),
                                             amount=close_data.get('turnoverValue'),
                                             preClosePrice=close_data.get('preClosePrice'),
                                             returnRate=None,
                                             quoteTime=None)

            elif instrument.instrumentType == InstrumentType.OPTION.name and \
                    instrument.assetClass == AssetClassType.EQUITY.name:
                std_quote_close = QuoteClose(instrumentId=instrument.instrumentId,
                                             tradeDate=close_data.get('tradeDate'),
                                             openPrice=close_data.get('openPrice'),
                                             closePrice=close_data.get('closePrice'),
                                             highPrice=close_data.get('highestPrice'),
                                             lowPrice=close_data.get('lowestPrice'),
                                             settlePrice=close_data.get('settlPrice'),
                                             volume=close_data.get('turnoverVol'),
                                             amount=close_data.get('turnoverValue'),
                                             preClosePrice=close_data.get('preClosePrice'),
                                             returnRate=None,
                                             quoteTime=None)
            elif instrument.instrumentType == InstrumentType.OPTION.name and \
                    instrument.assetClass == AssetClassType.COMMODITY.name:
                std_quote_close = QuoteClose(instrumentId=instrument.instrumentId,
                                             tradeDate=close_data.get('tradeDate'),
                                             openPrice=close_data.get('openPrice'),
                                             closePrice=close_data.get('closePrice'),
                                             highPrice=close_data.get('highestPrice'),
                                             lowPrice=close_data.get('lowestPrice'),
                                             settlePrice=close_data.get('settlPrice'),
                                             volume=close_data.get('turnoverVol'),
                                             amount=close_data.get('turnoverValue'),
                                             preClosePrice=close_data.get('preClosePrice'),
                                             returnRate=None,
                                             quoteTime=None)
            else:
                std_quote_close = None
            if std_quote_close.closePrice is None:
                logging.warning("?????????%s : %s??????close price!" % (instrument.instrumentId, std_quote_close.tradeDate))
                std_quote_close.closePrice = std_quote_close.settlePrice
            if std_quote_close.preClosePrice is None:
                logging.warning("?????????%s : %s??????pre close price!" % (instrument.instrumentId, std_quote_close.tradeDate))
                std_quote_close.preClosePrice = close_data.get('preSettlePrice')

            if instrument.listedDate == std_quote_close.tradeDate:
                logging.info('??????%s???????????????%s???????????????' % (instrument.instrumentId,
                                                   DateTimeUtils.date2str(instrument.listedDate)))
                return_rate = None
            elif std_quote_close.preClosePrice == 0:
                logging.info(
                    '??????????????????????????????0: [%s, %s]' % (instrument.instrumentId, DateTimeUtils.date2str(instrument.listedDate)))
                return_rate = None
            else:
                return_rate = (std_quote_close.closePrice - std_quote_close.preClosePrice) \
                              / std_quote_close.preClosePrice
                # print(return_rate)
            std_quote_close.returnRate = return_rate
            std_quote_close.updatedAt = datetime.now()
            return std_quote_close
            # ?????? : ????????????API ?????????TICK
        except Exception as e:
            logging.error(
                '????????????????????????????????????[%s], ?????????%s' % (
                    instrument.instrumentId, e))
            return None

    @staticmethod
    def load_close_data(instrument, early_date, late_date):
        client = Client()
        # TODO ????????????????????????????????????????????????
        logging.info(
            '??????????????????????????????[%s]' % (
                instrument.instrumentId))
        version = 'v1'
        tonglian_instrument = converter_utils.convertToTonglian(Instrument(
            instrumentId=instrument.instrumentId, instrumentType=instrument.instrumentType))
        if instrument.instrumentType == InstrumentType.STOCK.name:
            url = '/api/market/getMktEqud.json?field=&beginDate=%s&endDate=%s&ticker=%s' \
                  % (early_date.strftime('%Y%m%d'),
                     late_date.strftime('%Y%m%d'),
                     tonglian_instrument.instrumentId)
        elif instrument.instrumentType == InstrumentType.FUND.name:
            url = '/api/market/getMktFundd.json?field=&beginDate=%s&endDate=%s&ticker=%s' \
                  % (early_date.strftime('%Y%m%d'),
                     late_date.strftime('%Y%m%d'),
                     tonglian_instrument.instrumentId)
        elif instrument.instrumentType == InstrumentType.INDEX.name:
            url = '/api/market/getMktIdxd.json?field=&beginDate=%s&endDate=%s&ticker=%s' \
                  % (early_date.strftime('%Y%m%d'),
                     late_date.strftime('%Y%m%d'),
                     tonglian_instrument.instrumentId)
        # ????????????EQUITY / COMMODITY
        elif instrument.instrumentType == InstrumentType.FUTURE.name:
            url = '/api/market/getMktFutd.json?field=&beginDate=%s&endDate=%s&ticker=%s' \
                  % (early_date.strftime('%Y%m%d'),
                     late_date.strftime('%Y%m%d'),
                     tonglian_instrument.instrumentId.lower())
        elif instrument.instrumentType == InstrumentType.OPTION.name and \
                instrument.assetClass == AssetClassType.EQUITY.name:
            url = '/api/market/getMktOptd.json?field=&beginDate=%s&endDate=%s&optID=%s' \
                  % (early_date.strftime('%Y%m%d'),
                     late_date.strftime('%Y%m%d'),
                     tonglian_instrument.instrumentId.lower())
        elif instrument.instrumentType == InstrumentType.OPTION.name and \
                instrument.assetClass == AssetClassType.COMMODITY.name:
            url = '/api/market/getMktOptd.json?field=&beginDate=%s&endDate=%s&ticker=%s' \
                  % (early_date.strftime('%Y%m%d'),
                     late_date.strftime('%Y%m%d'),
                     tonglian_instrument.instrumentId.lower())
        # TODO ????????????????????????????????? : getMktSpotd getMktBondd getMktComIdxd
        else:
            logging.info(
                "?????????????????????????????????%s -> %s" % (instrument.instrumentId, instrument.instrumentType))
            return None

        # ???????????????????????????
        code, result = client.getData(url)
        logging.debug(result)

        if code != 200:
            logging.error(
                "??????????????????????????????[%s], ???????????????????????????????????????: %d" % (
                    instrument.instrumentId, code))
            logging.error(result)
            return None

        ret = json.loads(result)
        if ret['retCode'] != 1:
            logging.error(
                "??????????????????????????????[%s], ???????????????: %s" % (
                    instrument.instrumentId, ret))
            return None

        close_datas = ret['data']
        return close_datas

    def chk_one_quote_close(self, quote_close, existing_quote_close_list, force_update):
        if quote_close is not None:
            if quote_close.tradeDate not in existing_quote_close_list:
                return quote_close
        else:
            return None

    def upsert_one_quote_close(self, db_session, quote_close, existing_quote_close_dict, force_update):
        QuoteCloseBackfillerRepo.upsert_one_quote_close(db_session, quote_close, existing_quote_close_dict,
                                                        force_update)

    def backfill_multiple_quote_close(self, db_session, instrument, quote_close_breaks, force_update=False):
        logging.info("??????backfilling")
        # ??????????????????????????????
        new_quote_closes = []
        missing_success_count = 0
        missing_failed_count = 0
        try:
            # TODO ???Break?????????????????? MISSING or EXTRA
            missing_trade_dates = []
            # quote_close_breaks : [std.QuoteCloseBreak].(uuid, instrument_type,instrument_id, trade_date,...)
            for item in quote_close_breaks:
                if item.breakType == BreakType.MISSING.name:
                    missing_trade_dates.append(item.tradeDate)
            # print(missing_trade_dates)
            if missing_trade_dates:
                early_date, late_date = min(missing_trade_dates), max(missing_trade_dates)
            else:
                logging.info("?????????%s??????????????????break" % instrument.instrumentId)
                return True

            # ?????????????????????QuoteClose??????
            # TODO: ?????????????????????????????????????????????closes??????
            existing_quote_closes = QuoteCloseRepo.get_instrument_quote_closes(db_session, instrument)
            existing_quote_close_dict = {}
            existing_quote_close_list = []
            for new_quote_close in existing_quote_closes:
                existing_quote_close_dict[new_quote_close.tradeDate] = new_quote_close
                existing_quote_close_list.append(DateTimeUtils.date2str(new_quote_close.tradeDate))

            # ???TickSnapshot??????????????????Close??????????????? : 1 instrument - n dates
            # tick_snapshots = data_model_utils.load_tick_snapshots(db_session, instrument, missing_trade_dates)

            # ???API??????????????? ?????????????????????????????????????????? dict[date] = {jsonfy close data}
            close_datas = self.load_close_data(instrument, early_date, late_date)
            # print(existing_quote_close_dict.keys())
            if close_datas:
                quote_close_list = []
                for close_data in close_datas:
                    quote_close = self.calc_one_quote_close(instrument, close_data)
                    quote_close = self.chk_one_quote_close(quote_close, existing_quote_close_list, force_update)
                    if quote_close:
                        quote_close_list.append(quote_close)
                QuoteCloseBackfillerRepo.upsert_instrument_close_list(db_session, quote_close_list, instrument)
                return True
            else:
                logging.info("????????????????????????")
                return True

        except Exception as e:
            logging.error("???????????? %s" % e)
            return False

    # from base_backfiller
    def backfill_one_instrument(self, params):
        instrument, start_date, end_date, holidays, force_update = params
        logging.info("??????Backfill?????????: %s, startDate: %s, endDate: %s" % (instrument.instrumentId,
                                                                        start_date.strftime('%Y%m%d'),
                                                                        end_date.strftime('%Y%m%d')))
        db_session = std.create_db_session()
        try:
            quote_close_breaks = self.get_recon_breaks(db_session, start_date, end_date, instrument,
                                                       ReconFromType.QUOTE_CLOSE_RECON)
            ret = self.backfill_multiple_quote_close(db_session, instrument, quote_close_breaks, force_update)
            return instrument, ret
        except Exception as e:
            return instrument, False
        finally:
            db_session.close()


if __name__ == '__main__':
    # TODO FORCE_UPDATE = True
    QuoteCloseBackfillerService().backfill_all_instruments(start_date=date(2019, 8, 26),
                                                           end_date=date(2019, 8, 26),
                                                           active_only=True,
                                                           force_update=False)
