from terminal.dbo import Instrument
from sqlalchemy import or_, and_
from sqlalchemy import distinct
from terminal.utils import Logging
from terminal.dto import InstrumentStatusType, InstrumentType, AssetClassType
from terminal.conf.settings import WingModelConfig

logging = Logging.getLogger(__name__)


class InstrumentRepo:
    @staticmethod
    def get_instrument(db_session, instrument_id):
        instrument = db_session.query(Instrument).filter(Instrument.instrumentId == instrument_id).one_or_none()
        return instrument

    @staticmethod
    def get_instrument_id_list(db_session, trade_date, filtering=True):
        instrument_id_list = []
        if filtering:
            dbo_list = db_session.query(Instrument.instrumentId).filter(
                and_(
                    or_(Instrument.listedDate.is_(None), Instrument.listedDate <= trade_date),
                    or_(Instrument.delistedDate.is_(None), Instrument.delistedDate > trade_date))).all()
        else:
            dbo_list = db_session.query(Instrument.instrumentId).all()
        for dbo in dbo_list:
            instrument_id_list.append(dbo.instrumentId)
        return instrument_id_list

    @staticmethod
    def get_grouped_instrument_id_list(db_session, trade_date):
        dbo_list = db_session.query(Instrument).filter(
            and_(
                or_(Instrument.listedDate.is_(None), Instrument.listedDate <= trade_date),
                or_(Instrument.delistedDate.is_(None), Instrument.delistedDate > trade_date))).all()
        contract_types = set([d.contractType for d in dbo_list])
        instrument_id_map = {t: [] for t in contract_types}
        for dbo in dbo_list:
            instrument_id_map[dbo.contractType].append(dbo.instrumentId)
        instrument_id_list = []
        for i in instrument_id_map[None]:
            instrument_id_list.append([i])
        del instrument_id_map[None]
        for i in instrument_id_map.values():
            instrument_id_list.append(i)
        return instrument_id_list

    @staticmethod
    def get_commodity_variety_types(db_session, active_only=False):
        if active_only:
            get_type = db_session.query(distinct(Instrument.contractType)).filter(
                Instrument.status == InstrumentStatusType.ACTIVE.name).all()
            variety_types = [_[0] for _ in get_type if _[0]]
        else:
            get_type = db_session.query(distinct(Instrument.contractType)).all()
            variety_types = [_[0] for _ in get_type if _[0]]
        return variety_types

    @staticmethod
    def get_all_instrument(db_session):
        """
        ??????????????????
        :param db_session:
        :return:
        """
        logging.info('????????????instrument??????????????????')

        all_instruments = db_session.query(Instrument).all()

        if all_instruments is None or len(all_instruments) == 0:
            logging.info('?????????instrument????????????????????????')
            return []

        logging.info('???instrument???????????????%d?????????' %
                     (len(all_instruments)))

        return all_instruments

    @staticmethod
    def get_instrument_contract_type(db_session, underlyer):
        if WingModelConfig.data_source.get(underlyer) is not None:
            return WingModelConfig.data_source[underlyer]
        else:
            underlyer = db_session.query(Instrument.contractType).filter(
                Instrument.instrumentId == underlyer).first()[0]
            return WingModelConfig.data_source[underlyer]

    @staticmethod
    def get_instrument_by_contract_in_range(db_session, contract_type, listed_date, delisted_date):
        instrument_ids = db_session.query(Instrument.instrumentId).filter(
            Instrument.contractType == contract_type,
            listed_date <= Instrument.delistedDate,
            delisted_date >= Instrument.listedDate).all()
        return [_[0] for _ in instrument_ids if _[0]]

    @staticmethod
    def get_active_instruments(db_session, active_only=True):
        if active_only:
            all_instruments = db_session.query(Instrument).filter(
                Instrument.status == InstrumentStatusType.ACTIVE.name).all()
        else:
            all_instruments = db_session.query(Instrument).all()
        all_instruments_str = []
        for item in all_instruments:
            all_instruments_str.append(item.__str__())
        logging.info("Load???????????????%d???, ???%s???" % (len(all_instruments_str), ",".join(all_instruments_str)))
        return all_instruments

    @staticmethod
    def load_instruments_in_option_conf(db_session, active_only=False):
        if active_only:
            all_instruments = db_session.query(Instrument).filter(
                or_(and_(Instrument.instrumentType == InstrumentType.FUTURE.name,
                         Instrument.assetClass == AssetClassType.COMMODITY.name,
                         Instrument.contractType.in_(WingModelConfig.data_source),
                         Instrument.status == InstrumentStatusType.ACTIVE.name),
                    and_(Instrument.instrumentType == InstrumentType.OPTION.name, InstrumentStatusType.ACTIVE.name),
                    and_(Instrument.instrumentId.in_(WingModelConfig.data_source),
                         InstrumentStatusType.ACTIVE.name))).all()
        else:
            all_instruments = db_session.query(Instrument).filter(
                or_(and_(Instrument.instrumentType == InstrumentType.FUTURE.name,
                         Instrument.assetClass == AssetClassType.COMMODITY.name,
                         Instrument.contractType.in_(WingModelConfig.data_source)),
                    Instrument.instrumentType == InstrumentType.OPTION.name,
                    Instrument.instrumentId.in_(WingModelConfig.data_source))).all()
        return all_instruments

    @staticmethod
    def update_instrument_status(db_session):
        try:
            # TODO: ????????????????????????????????????
            # TODO???Step1: ?????????????????????????????????????????????????????????????????????????????????
            # TODO???Step2: ???????????????????????????????????????INACTIVE
            # TODO???Step3: ????????????????????????????????????ACTIVE
            db_session.query(Instrument).filter(
                (Instrument.instrumentType == InstrumentType.FUTURE.name)).update(
                {'status': InstrumentStatusType.ACTIVE.name})
            db_session.query(Instrument).filter(
                (Instrument.instrumentType == InstrumentType.OPTION.name)).update(
                {'status': InstrumentStatusType.ACTIVE.name})
            db_session.flush()
            db_session.commit()
        finally:
            db_session.close()

    @staticmethod
    def upsert_instrument_list(db_session, instrument_list):
        db_session.bulk_save_objects(instrument_list)
        db_session.flush()
        db_session.commit()
        logging.info('???????????????????????????')

    @staticmethod
    def load_instruments_dict(db_session, active_only):
        instruments = InstrumentRepo.get_active_instruments(db_session, active_only)
        instruments_dict = {}
        for instrument in instruments:
            instruments_dict[instrument.instrumentId] = instrument
        return instruments_dict

    @staticmethod
    def get_instruments_by_trade_date(db_session, trade_date):
        instruments = db_session.query(Instrument.instrumentId, Instrument.contractType).filter(
            and_(
                or_(Instrument.listedDate.is_(None), Instrument.listedDate <= trade_date),
                or_(Instrument.delistedDate.is_(None), Instrument.delistedDate > trade_date))).all()
        return instruments

    @staticmethod
    def delecte_instrumet_by_instrument_ids(db_session, instrument_ids):
        """
        ??????instrument_id??????????????????????????????
        :param db_session:
        :param instrument_ids:
        :return:
        """
        logging.info('????????????????????????: %d ' % len(instrument_ids))
        db_session.query(Instrument). \
            filter(Instrument.instrumentId.in_(instrument_ids)). \
            delete(synchronize_session=False)
        db_session.commit()
        logging.info('??????????????????')

    @staticmethod
    def get_instrument_by_source(db_session, source):
        """
        ???????????????data_source??????????????????
        :param db_session:
        :param source:
        :return:
        """
        logging.info('????????????data_source???: %s???????????????' % source)
        instruments = db_session.query(Instrument). \
            filter(Instrument.dataSource == source).all()

        if instruments is None or len(instruments) == 0:
            logging.info('???instrument????????????????????????????????????')
            return []
        logging.info('???instrument???????????????%d?????????' %
                     (len(instruments)))
        return instruments

    @staticmethod
    def get_instruments_by_ids(db_session, instrument_ids):
        """
        ??????instrument_ids??????????????????
        :param db_session:
        :param instrument_ids:
        :return:
        """
        logging.info('???????????????????????????: %d,?????????: %s' % (len(instrument_ids), instrument_ids))
        instruments = db_session.query(Instrument).filter(Instrument.instrumentId.in_(instrument_ids)).all()
        if len(instruments) == 0:
            logging.info('???????????????????????????')
            return []
        logging.info('???????????????????????????: %d,?????????: %s' % (len(instruments), [_.instrumentId for _ in instruments]))
        return instruments
