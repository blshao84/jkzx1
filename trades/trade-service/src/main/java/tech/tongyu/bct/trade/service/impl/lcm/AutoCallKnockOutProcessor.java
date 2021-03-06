package tech.tongyu.bct.trade.service.impl.lcm;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tech.tongyu.bct.cm.product.asset.Asset;
import tech.tongyu.bct.cm.product.iov.InstrumentOfValue;
import tech.tongyu.bct.cm.product.iov.impl.AnnualizedAutoCallOption;
import tech.tongyu.bct.cm.trade.LCMEventTypeEnum;
import tech.tongyu.bct.cm.trade.impl.BctTrade;
import tech.tongyu.bct.cm.trade.impl.BctTradePosition;
import tech.tongyu.bct.common.exception.CustomException;
import tech.tongyu.bct.common.util.JsonUtils;
import tech.tongyu.bct.trade.dao.dbo.LCMEvent;
import tech.tongyu.bct.trade.dao.repo.LCMEventRepo;
import tech.tongyu.bct.trade.dto.event.LCMEventDTO;
import tech.tongyu.bct.trade.dto.lcm.LCMNotificationInfoDTO;
import tech.tongyu.bct.trade.service.LCMProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class AutoCallKnockOutProcessor extends ExerciseProcessorCommon implements LCMProcessor {

    private LCMEventRepo lcmEventRepo;

    private static final String KNOCK_OUT_DATE = "knockOutDate";

    @Autowired
    public AutoCallKnockOutProcessor(LCMEventRepo lcmEventRepo) {
        this.lcmEventRepo = lcmEventRepo;
    }

    @Override
    public LCMEventTypeEnum eventType() {
        return LCMEventTypeEnum.KNOCK_OUT;
    }

    @Override
    public List<LCMNotificationInfoDTO> notifications(Asset<InstrumentOfValue> asset) {
        return new ArrayList<>();
    }

    @Override
    public boolean canPreSettle() {
        return true;
    }

    @Override
    public BigDecimal preSettle(BctTradePosition position, LCMEventDTO eventDto) {
        checkPositionStatus(position, eventDto.getEventDetail());
        Map<String, Object> eventDetail = eventDto.getEventDetail();
        InstrumentOfValue instrument = position.asset.instrumentOfValue();
        BigDecimal settleAmount = calAutoCallKnockOutPrice(instrument, eventDetail);
        return getCashFlowValueByPartyRole(settleAmount, position.partyRole());
    }

    @Override
    @Transactional
    public List<BctTradePosition> process(BctTrade trade, BctTradePosition position, LCMEventDTO eventDto) {
        Map<String, Object> eventDetail = eventDto.getEventDetail();
        checkPositionStatus(position, eventDetail);

        InstrumentOfValue instrument = position.asset.instrumentOfValue();
        String settleAmountStr = (String) eventDetail.get(SETTLE_AMOUNT);
        if (StringUtils.isBlank(settleAmountStr)){
            throw new CustomException("?????????????????????????????????settleAmount");
        }
        BigDecimal settleAmount = new BigDecimal(settleAmountStr);
        BigDecimal premium = getInitialPremium(position);

        LCMEvent lcmEvent = new LCMEvent();
        BeanUtils.copyProperties(eventDto, lcmEvent);
        lcmEvent.setEventDetail(JsonUtils.mapper.valueToTree(eventDetail));
        lcmEvent.setPaymentDate(getPaymentDate(eventDetail));
        lcmEvent.setEventType(eventDto.getLcmEventType());
        lcmEvent.setCashFlow(settleAmount);
        lcmEvent.setPremium(premium);
        lcmEventRepo.save(lcmEvent);

        sendPositionDoc(trade, position);
        position.setLcmEventType(eventDto.getLcmEventType());
        BctTradePosition newPosition = new BctTradePosition();
        BeanUtils.copyProperties(position, newPosition);
        return Arrays.asList(newPosition);
    }

    private void checkPositionStatus(BctTradePosition position,  Map<String, Object> eventDetail){
        if (isStatusError(position.lcmEventType)) {
            throw new CustomException(String.format("????????????[%s],??????????????????[%s],????????????????????????",
                    position.positionId, position.getLcmEventType().description()));
        }
        InstrumentOfValue instrument = position.asset.instrumentOfValue();
        if (!(instrument instanceof AnnualizedAutoCallOption)) {
            throw new CustomException(String.format("AutoCallKnockOutProcessor?????????AutoCallOption," +
                            "???Position[%s]???????????????[%s]?????????", position.getPositionId(), instrument.getClass()));
        }
        String knockOutDateStr = (String) eventDetail.get(KNOCK_OUT_DATE);
        if (StringUtils.isBlank(knockOutDateStr)){
            throw new CustomException("?????????????????????knockOutDate");
        }
        // ????????????????????????????????????
        AnnualizedAutoCallOption iov = (AnnualizedAutoCallOption) instrument;
        LocalDate knockOutDate = LocalDate.parse(knockOutDateStr);
        LocalDate effectiveDate = iov.effectiveDate();
        if (knockOutDate.isBefore(effectiveDate)){
            throw new CustomException(String.format("???????????????????????????,?????????:[%s],?????????:[%s]",
                    effectiveDate.toString(), knockOutDateStr));
        }

    }

    private BigDecimal calAutoCallKnockOutPrice(InstrumentOfValue instrument, Map<String, Object> eventDetail) {
        String knockOutDateStr = (String) eventDetail.get(KNOCK_OUT_DATE);
        if (StringUtils.isBlank(knockOutDateStr)){
            throw new CustomException("?????????????????????");
        }
        String underlyerPriceStr = (String) eventDetail.get(UNDERLYER_PRICE);
        if (StringUtils.isBlank(underlyerPriceStr)){
            throw new CustomException("????????????????????????");
        }
        LocalDate knockOutDate = LocalDate.parse(knockOutDateStr);
        BigDecimal underlyerPrice = new BigDecimal(underlyerPriceStr);

        AnnualizedAutoCallOption iov = (AnnualizedAutoCallOption) instrument;
//        // ?????????????????????????????????
//        BigDecimal observationPrice;
//        Map<LocalDate, BigDecimal> fixingObservations = iov.fixingObservations();
//        if (fixingObservations.containsKey(knockOutDate)){
//            observationPrice = fixingObservations.get(knockOutDate);
//        } else {
//            LocalDate nearestObservationDate = getNearestObservationDateByKnockOutDate(knockOutDate,
//                    new ArrayList<>(fixingObservations.keySet()));
//            if (Objects.isNull(nearestObservationDate)){
//                throw new CustomException(String.format("?????????:[%s],??????????????????????????????", knockOutDateStr));
//            }
//            observationPrice = fixingObservations.get(nearestObservationDate);
//        }
//        // ?????????????????????????????????????????????,????????????????????????
//        if (Objects.isNull(observationPrice)){
//            throw new CustomException(String.format("?????????:[%s]??????????????????", knockOutDate.toString()));
//        }
//        if (underlyerPrice.compareTo(observationPrice) < 0){
//            throw new CustomException(String.format("????????????????????????????????????,????????????????????????",
//                    underlyerPriceStr, observationPrice.toPlainString()));
//        }
        LocalDate effectiveDate = iov.effectiveDate();
        BigDecimal interval = BigDecimal.valueOf(effectiveDate.until(knockOutDate, ChronoUnit.DAYS));
        BigDecimal notional = iov.notionalAmountFaceValue().multiply(iov.participationRate());
        BigDecimal daysInYear = iov.daysInYear();
        BigDecimal coupon = iov.couponPayment();
        if (BigDecimal.ZERO.compareTo(daysInYear) == 0){
            throw new CustomException("?????????????????????0");
        }
        //????????????*coupon???%???*???????????????-????????????/??????????????????
        return notional.multiply(coupon).multiply(interval).divide(daysInYear, 10, BigDecimal.ROUND_DOWN);
    }

    private LocalDate getNearestObservationDateByKnockOutDate(LocalDate knockOutDate, List<LocalDate> observationDates){
        Collections.sort(observationDates);
        LocalDate nearestObservationDate = null;
        for (LocalDate observationDate: observationDates) {
            if (observationDate.isBefore(knockOutDate)){
                nearestObservationDate = observationDate;
            }
            if (observationDate.isAfter(knockOutDate)){
                break;
            }
        }
        return nearestObservationDate;
    }
}
