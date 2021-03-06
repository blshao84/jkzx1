package tech.tongyu.bct.trade.api;

import com.google.common.collect.Lists;
import io.vavr.Tuple2;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import tech.tongyu.bct.acl.common.UserInfo;
import tech.tongyu.bct.auth.dto.ResourceDTO;
import tech.tongyu.bct.auth.service.ResourcePermissionService;
import tech.tongyu.bct.auth.service.ResourceService;
import tech.tongyu.bct.cm.core.BusinessCenterEnum;
import tech.tongyu.bct.cm.product.iov.InstrumentAssetClassTypeEnum;
import tech.tongyu.bct.cm.product.iov.ProductTypeEnum;
import tech.tongyu.bct.cm.product.iov.feature.OptionTypeEnum;
import tech.tongyu.bct.cm.trade.LCMEventTypeEnum;
import tech.tongyu.bct.cm.trade.TradeStatusEnum;
import tech.tongyu.bct.cm.trade.impl.BctTrade;
import tech.tongyu.bct.common.api.annotation.BctApiTagEnum;
import tech.tongyu.bct.common.api.annotation.BctExcelTypeEnum;
import tech.tongyu.bct.common.api.annotation.BctMethodArg;
import tech.tongyu.bct.common.api.annotation.BctMethodInfo;
import tech.tongyu.bct.common.api.response.RpcResponseListPaged;
import tech.tongyu.bct.common.util.BeanUtil;
import tech.tongyu.bct.common.util.CollectionUtils;
import tech.tongyu.bct.common.util.JsonUtils;
import tech.tongyu.bct.common.util.ProfilingUtils;
import tech.tongyu.bct.trade.dto.trade.TradeDTO;
import tech.tongyu.bct.trade.dto.trade.TradePositionIndexDTO;
import tech.tongyu.bct.trade.dto.trade.common.TradeReferenceDTO;
import tech.tongyu.bct.trade.dto.trade.common.UnitTypeEnum;
import tech.tongyu.bct.trade.dto.trade.product.*;
import tech.tongyu.bct.trade.service.PortfolioSearchService;
import tech.tongyu.bct.trade.service.PortfolioService;
import tech.tongyu.bct.trade.service.TradeService;
import tech.tongyu.bct.trade.service.impl.transformer.DefaultingRules;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.Arrays;
import java.util.stream.Collectors;

import static tech.tongyu.bct.auth.enums.ResourcePermissionTypeEnum.*;
import static tech.tongyu.bct.auth.enums.ResourceTypeEnum.BOOK;

@Service
public class TradeApi {
    private static Logger logger = LoggerFactory.getLogger(TradeApi.class);

    @Autowired
    TradeService tradeService;
    @Autowired
    PortfolioService portfolioService;
    @Autowired
    PortfolioSearchService portfolioSearchService;
    @Autowired
    ResourceService resourceService;
    @Autowired
    ResourcePermissionService resourcePermissionService;

    @BctMethodInfo
    public Boolean trdTradeDeleteAll() {
        tradeService.deleteAll();
        return true;
    }

    @BctMethodInfo
    public List<TradeDTO> trdTradeLivedList() {
        return ProfilingUtils.timed("fetch live trades", () -> tradeService.findByTradeStatus(TradeStatusEnum.LIVE));
    }


    @BctMethodInfo
    public Boolean trdTradeGenerateHistoryIndex() {
        tradeService.generateHistoryTradeIndex();
        return true;
    }

    @BctMethodInfo(
            description = "???????????????validTime??????????????????Trade",
            retDescription = "??????????????????",
            retName = "true or false",
            service = "trade-service"
    )
    public Boolean trdTradeCreate(
            @BctMethodArg(description = "????????????") String validTime,
            @BctMethodArg(description = "????????????", argClass = TradeDTO.class) Map<String, Object> trade
    ) {
        LocalDateTime validDateTime = LocalDateTime.parse(validTime);
        TradeDTO tradeDTO = JsonUtils.mapper.convertValue(trade, TradeDTO.class);
        if (StringUtils.isBlank(tradeDTO.getBookName())) {
            throw new IllegalArgumentException("????????????????????????(bookName)");
        }
        Collection<ResourceDTO> resources = resourceService.authBookGetCanRead();
        boolean hasBookName = resources.stream()
                .anyMatch(resourceDTO -> tradeDTO.getBookName().equals(resourceDTO.getResourceName()));
        if (!hasBookName) {
            throw new IllegalArgumentException(String.format("???????????????:%s,???????????????", tradeDTO.bookName));
        }

        if (resourcePermissionService.authCan(BOOK.name(), Lists.newArrayList(tradeDTO.bookName), CREATE_TRADE.name())
                .stream().noneMatch(permission -> permission)) {
            throw new IllegalArgumentException(String.format("????????????????????????:%s,????????????????????????,??????????????????", tradeDTO.bookName));
        }

        tradeService.create(tradeDTO, OffsetDateTime.of(validDateTime, DefaultingRules.defaultZone));
        logger.info(String.format("successfully create trade with tradeId=%s in book=%s", tradeDTO.tradeId, tradeDTO.bookName));
        return true;
    }

    @BctMethodInfo(
            description = "???????????????validTime??????????????????Trade",
            retDescription = "????????????????????????????????????",
            retName = "List<String>",
            service = "trade-service"
    )
    public List<String> trdTradeCreateBatchForJKZX(
            @BctMethodArg(description = "????????????") String validTime,
            @BctMethodArg(description = "????????????", argClass = TradeDTO.class) List<Map<String, Object>> trades
    ) {
        OffsetDateTime validDateTime = OffsetDateTime.of(LocalDateTime.parse(validTime), DefaultingRules.defaultZone);
        Collection<String> books = resourceService.authBookGetCanRead().stream()
                .map(ResourceDTO::getResourceName)
                .collect(Collectors.toList());
        List<String> failed = Lists.newArrayList();

        trades.parallelStream()
                .map(trade -> JsonUtils.mapper.convertValue(trade, TradeDTO.class))
                .collect(Collectors.toList())
                .forEach(tradeDTO -> {
                    String tradeId = tradeDTO.tradeId;
                    try {
                        String bookName = tradeDTO.getBookName();
                        if (StringUtils.isBlank(bookName)) {
                            throw new IllegalArgumentException("????????????????????????(bookName)");
                        }

                        if (!books.contains(bookName)) {
                            throw new IllegalArgumentException(String.format("???????????????:%s,???????????????", tradeDTO.bookName));
                        }

                        tradeService.create(tradeDTO, validDateTime);
                        logger.info(String.format("created trade %s in book=%s", tradeId, bookName));
                    } catch (DataIntegrityViolationException e) {
                        if (e.getCause() instanceof ConstraintViolationException) {
                            failed.add(String.format("failed create trade %s : ????????????:%s,????????????", tradeId, tradeId));
                        }
                    } catch (Exception e) {
                        failed.add(String.format("failed create trade %s : %s", tradeId, e.getMessage()));
                    }
                });
        return failed;
    }

    @BctMethodInfo(
            description = "??????????????????",
            retDescription = "??????????????????",
            retName = "true or false",
            service = "trade-service"
    )
    public Boolean trdTradeStatusUpdate(
            @BctMethodArg(description = "????????????") String tradeId,
            @BctMethodArg(description = "????????????", argClass = TradeStatusEnum.class) String tradeStatus
    ) {
        TradeDTO tradeDTO = tradeService.getByTradeId(tradeId, OffsetDateTime.of(LocalDateTime.now(), DefaultingRules.defaultZone),
                OffsetDateTime.of(LocalDateTime.now(), DefaultingRules.defaultZone));

        if (resourcePermissionService.authCan(BOOK.name(), Lists.newArrayList(tradeDTO.bookName), UPDATE_TRADE.name())
                .stream().noneMatch(permission -> permission)) {
            throw new IllegalArgumentException(String.format("????????????????????????:%s,????????????????????????,??????????????????", tradeDTO.bookName));
        }


        tradeService.updateTradeStatus(tradeId, TradeStatusEnum.valueOf(tradeStatus));
        return true;
    }

    @BctMethodInfo(
            description = "???????????????????????????",
            service = "trade-service"
    )
    public TradeDTO trdTradeGet(
            @BctMethodArg(description = "????????????") String tradeId,
            @BctMethodArg(description = "????????????") String validTime,
            @BctMethodArg(description = "????????????") String transactionTime
    ) {
        if (StringUtils.isBlank(tradeId)) throw new IllegalArgumentException(String.format("??????????????????????????????tradeId"));
        LocalDateTime validDateTime = LocalDateTime.parse(validTime);
        LocalDateTime transactionDateTime = LocalDateTime.parse(transactionTime);

        TradeDTO tradeDTO = tradeService.getByTradeId(tradeId, OffsetDateTime.of(validDateTime, DefaultingRules.defaultZone),
                OffsetDateTime.of(transactionDateTime, DefaultingRules.defaultZone));

        if (resourcePermissionService.authCan(BOOK.name(), Lists.newArrayList(tradeDTO.bookName), READ_TRADE.name())
                .stream().noneMatch(permission -> permission)) {
            throw new IllegalArgumentException(String.format("????????????????????????:%s,????????????????????????,??????????????????", tradeDTO.bookName));
        }

        return tradeDTO;
    }

    @BctMethodInfo(
            description = "???????????????????????????",
            retDescription = "??????????????????",
            retName = "true or false",
            service = "trade-service"
    )
    public Boolean trdTradeDelete(
            @BctMethodArg(description = "????????????") String tradeId,
            @BctMethodArg(description = "????????????") String validTime,
            @BctMethodArg(description = "????????????") String transactionTime
    ) {
        if (StringUtils.isBlank(tradeId)) throw new IllegalArgumentException(String.format("??????????????????????????????tradeId"));
        LocalDateTime validDateTime = LocalDateTime.parse(validTime);
        LocalDateTime transactionDateTime = LocalDateTime.parse(transactionTime);

        TradeDTO tradeDTO = tradeService.getByTradeId(tradeId, OffsetDateTime.of(validDateTime, DefaultingRules.defaultZone),
                OffsetDateTime.of(transactionDateTime, DefaultingRules.defaultZone));

        if (resourcePermissionService.authCan(BOOK.name(), Lists.newArrayList(tradeDTO.bookName), DELETE_TRADE.name())
                .stream().noneMatch(permission -> permission)) {
            throw new IllegalArgumentException(String.format("????????????????????????:%s,????????????????????????,??????????????????", tradeDTO.bookName));
        }

        tradeService.deleteByTradeId(tradeId, OffsetDateTime.of(validDateTime, DefaultingRules.defaultZone),
                OffsetDateTime.of(transactionDateTime, DefaultingRules.defaultZone));
        return true;
    }

    @BctMethodInfo(
            description = "????????????????????????????????????????????????",
            retDescription = "?????????????????????????????????????????????",
            retName = "list of instruments",
            service = "trade-service"
    )
    public List<String> trdInstrumentListByBook(
            @BctMethodArg(description = "???????????????") String bookName
    ) {
        return tradeService.listInstrumentsByBookName(bookName);
    }

    @BctMethodInfo(
            description = "??????????????????????????????",
            retDescription = "????????????????????????",
            retName = "list of trade ids",
            excelType = BctExcelTypeEnum.ArrayString,
            tags = {BctApiTagEnum.Excel},
            service = "trade-service"
    )
    public List<String> trdTradeListByBook(
            @BctMethodArg(description = "???????????????") String bookName
    ) {
        validateBook(bookName);
        return tradeService.listByBookName(bookName, null, null);
    }

    @BctMethodInfo(
            description = "??????????????????????????????????????????",
            retDescription = "??????????????????",
            retName = "list of trade ids",
            service = "trade-service"
    )
    public List<String> trdTradeIdListByCounterPartyName(
            @BctMethodArg(description = "????????????") String counterPartyName
    ) {
        if (StringUtils.isBlank(counterPartyName)) {
            throw new IllegalArgumentException("?????????????????????counterPartyName");
        }
        return tradeService.listTradeIdByCounterPartyName(counterPartyName);
    }

    @BctMethodInfo(
            description = "????????????????????????????????????????????????????????????",
            retDescription = "??????????????????????????????????????????????????????",
            retName = "list of TradeDTOs",
            returnClass = TradeDTO.class,
            service = "trade-service"
    )
    public List<TradeDTO> trdExpiringTradeList() {
        String userName = BeanUtil.getBean(UserInfo.class).getUserName();
        return filterTradeByReadableBook(tradeService.getExpiringTrades(userName, OffsetDateTime.now()));
    }

    @BctMethodInfo(
            description = "??????????????????",
            retDescription = "?????????????????????",
            retName = "list of trade ids",
            service = "trade-service"
    )
    public List<String> trdTradeListBySimilarTradeId(
            @BctMethodArg(description = "??????????????????") String similarTradeId
    ) {
        Set<String> bookNames = resourceService.authBookGetCanRead().stream().map(ResourceDTO::getResourceName).collect(Collectors.toSet());
        return tradeService.listBySimilarTradeId(similarTradeId).stream()
                .filter(v -> bookNames.contains(v.getBookName()))
                .map(BctTrade::getTradeId)
                .collect(Collectors.toList());
    }

    @BctMethodInfo(
            description = "?????????????????????????????????",
            retDescription = "?????????????????????",
            retName = "list of TradeDTOs",
            returnClass = TradeDTO.class,
            service = "trade-service"
    )
    public List<TradeDTO> trdTradeSearch(
            @BctMethodArg(required = false, description = "????????????") String tradeId,
            @BctMethodArg(required = false, description = "???????????????") String bookName,
            @BctMethodArg(required = false, description = "????????????") String salesName,
            @BctMethodArg(required = false, description = "????????????") String tradeDate,
            @BctMethodArg(required = false, description = "??????????????????") String counterPartyCode,
            @BctMethodArg(required = false, description = "??????????????????") List<String> portfolioNames
    ) {
        Map<String, String> searchDetail = new HashMap<>();
        searchDetail.put("tradeId", tradeId);
        searchDetail.put("bookName", bookName);
        searchDetail.put("salesName", salesName);
        searchDetail.put("tradeDate", tradeDate);
        searchDetail.put("counterPartyCode", counterPartyCode);
        List<TradeDTO> search_trades = ProfilingUtils.timed("search trades", () -> tradeService.search(searchDetail));
        return ProfilingUtils.timed("set portfolio", () -> filterAndSetPortfolioNames(portfolioNames, search_trades));
    }

    @BctMethodInfo(
            description = "???????????????????????????????????????",
            retDescription = "?????????????????????????????????",
            retName = "paged TradeDTOs",
            returnClass = TradeDTO.class,
            service = "trade-service"
    )
    public RpcResponseListPaged<TradeDTO> trdTradeSearchPaged(
            @BctMethodArg(description = "??????") Integer page,
            @BctMethodArg(description = "??????") Integer pageSize,
            @BctMethodArg(required = false, description = "????????????") String tradeId,
            @BctMethodArg(required = false, description = "???????????????") String bookName,
            @BctMethodArg(required = false, description = "????????????") String salesName,
            @BctMethodArg(required = false, description = "????????????") String tradeDate,
            @BctMethodArg(required = false, description = "??????????????????") String counterPartyCode,
            @BctMethodArg(required = false, description = "??????") String status,
            @BctMethodArg(required = false, description = "??????????????????") List<String> portfolioNames
    ) {
        if (Objects.isNull(page)) throw new IllegalArgumentException(String.format("???????????????????????????"));
        if (Objects.isNull(pageSize)) throw new IllegalArgumentException(String.format("???????????????????????????"));

        Map<String, String> searchDetail = new HashMap<>();
        searchDetail.put("tradeId", tradeId);
        searchDetail.put("bookName", bookName);
        searchDetail.put("salesName", salesName);
        searchDetail.put("tradeDate", tradeDate);
        searchDetail.put("counterPartyCode", counterPartyCode);
        searchDetail.put("status", status);

        List<TradeDTO> trades = filterAndSetPortfolioNames(portfolioNames,
                filterTradeByReadableBook(tradeService.search(searchDetail)));

        int start = page * pageSize;
        int end = Math.min(start + pageSize, trades.size());
        return new RpcResponseListPaged<>(trades.subList(start, end), trades.size());
    }

    @BctMethodInfo(
            description = "?????????????????????????????????",
            retDescription = "?????????????????????",
            retName = "list of TradeDTOs",
            returnClass = TradeDTO.class,
            service = "trade-service"
    )
    public List<TradeDTO> trdTradeSearchIndex(
            @BctMethodArg(required = false, description = "????????????") String tradeId,
            @BctMethodArg(required = false, description = "???????????????") String bookName,
            @BctMethodArg(required = false, description = "????????????") String salesName,
            @BctMethodArg(required = false, description = "????????????") String tradeDate,
            @BctMethodArg(required = false, description = "????????????", argClass = ProductTypeEnum.class) String
                    productType,
            @BctMethodArg(required = false, description = "????????????????????????", argClass = LCMEventTypeEnum.class) String
                    lcmEventType,
            @BctMethodArg(required = false, description = "???????????????") String instrumentId,
            @BctMethodArg(required = false, description = "????????????") String effectiveDate,
            @BctMethodArg(required = false, description = "????????????") String expirationDate,
            @BctMethodArg(required = false, description = "??????????????????") String counterPartyName,
            @BctMethodArg(required = false, description = "??????????????????") List<String> portfolioNames
    ) {
        TradePositionIndexDTO indexDto = new TradePositionIndexDTO();
        indexDto.setTradeId(StringUtils.isBlank(tradeId) ? null : tradeId);
        indexDto.setBookName(StringUtils.isBlank(bookName) ? null : bookName);
        indexDto.setSalesName(StringUtils.isBlank(salesName) ? null : salesName);
        indexDto.setInstrumentId(StringUtils.isBlank(instrumentId) ? null : instrumentId);
        indexDto.setTradeDate(StringUtils.isBlank(tradeDate) ? null : LocalDate.parse(tradeDate));
        indexDto.setCounterPartyName(StringUtils.isBlank(counterPartyName) ? null : counterPartyName);
        indexDto.setEffectiveDate(StringUtils.isBlank(effectiveDate) ? null : LocalDate.parse(effectiveDate));
        indexDto.setProductType(StringUtils.isBlank(productType) ? null : ProductTypeEnum.valueOf(productType));
        indexDto.setExpirationDate(StringUtils.isBlank(expirationDate) ? null : LocalDate.parse(expirationDate));
        indexDto.setLcmEventType(StringUtils.isBlank(lcmEventType) ? null : LCMEventTypeEnum.valueOf(lcmEventType));

        List<String> tradeIds = filterReadableBookTradeIdByIndex(tradeService.searchTradeIndexByIndex(indexDto, null));
        return filterAndFindPortfolioNames(portfolioNames, tradeIds, null, null)._1;
    }

    @BctMethodInfo(
            description = "???????????????????????????????????????",
            retDescription = "?????????????????????????????????",
            retName = "paged TradeDTOs",
            returnClass = TradeDTO.class,
            service = "trade-service"
    )
    public RpcResponseListPaged<TradeDTO> trdTradeSearchIndexPaged(
            @BctMethodArg(description = "??????") Integer page,
            @BctMethodArg(description = "??????") Integer pageSize,
            @BctMethodArg(required = false, description = "??????") String status,
            @BctMethodArg(required = false, description = "????????????") String tradeId,
            @BctMethodArg(required = false, description = "???????????????") String bookName,
            @BctMethodArg(required = false, description = "????????????") String salesName,
            @BctMethodArg(required = false, description = "????????????") String tradeDate,
            @BctMethodArg(required = false, description = "????????????", argClass = ProductTypeEnum.class) String
                    productType,
            @BctMethodArg(required = false, description = "????????????????????????", argClass = LCMEventTypeEnum.class) String
                    lcmEventType,
            @BctMethodArg(required = false, description = "???????????????") String instrumentId,
            @BctMethodArg(required = false, description = "????????????") String effectiveDate,
            @BctMethodArg(required = false, description = "????????????") String expirationDate,
            @BctMethodArg(required = false, description = "??????????????????") String counterPartyName,
            @BctMethodArg(required = false, description = "??????????????????") List<String> portfolioNames
    ) {
        TradePositionIndexDTO indexDto = new TradePositionIndexDTO();
        indexDto.setTradeId(StringUtils.isBlank(tradeId) ? null : tradeId);
        indexDto.setBookName(StringUtils.isBlank(bookName) ? null : bookName);
        indexDto.setSalesName(StringUtils.isBlank(salesName) ? null : salesName);
        indexDto.setInstrumentId(StringUtils.isBlank(instrumentId) ? null : instrumentId);
        indexDto.setTradeDate(StringUtils.isBlank(tradeDate) ? null : LocalDate.parse(tradeDate));
        indexDto.setCounterPartyName(StringUtils.isBlank(counterPartyName) ? null : counterPartyName);
        indexDto.setEffectiveDate(StringUtils.isBlank(effectiveDate) ? null : LocalDate.parse(effectiveDate));
        indexDto.setProductType(StringUtils.isBlank(productType) ? null : ProductTypeEnum.valueOf(productType));
        indexDto.setExpirationDate(StringUtils.isBlank(expirationDate) ? null : LocalDate.parse(expirationDate));
        indexDto.setLcmEventType(StringUtils.isBlank(lcmEventType) ? null : LCMEventTypeEnum.valueOf(lcmEventType));

        List<String> tradeIds = ProfilingUtils.timed("search trades by index",
                () -> filterReadableBookTradeIdByIndex(tradeService.searchTradeIndexByIndex(indexDto, status)));
        Tuple2<List<TradeDTO>, Integer> result = filterAndFindPortfolioNames(portfolioNames, tradeIds, page, pageSize);
        return new RpcResponseListPaged<>(result._1, result._2);
    }

    @BctMethodInfo(
            description = "???????????????????????????????????????",
            retDescription = "?????????????????????????????????",
            retName = "paged TradeDTOs",
            returnClass = TradeDTO.class,
            service = "trade-service"
    )
    public RpcResponseListPaged<TradeDTO> trdTradeSettleablePaged(
            @BctMethodArg(description = "??????") Integer page,
            @BctMethodArg(description = "??????") Integer pageSize
    ) {
        List<ProductTypeEnum> productTypes = Arrays.asList(ProductTypeEnum.VANILLA_EUROPEAN,
                ProductTypeEnum.VANILLA_AMERICAN);
        List<LCMEventTypeEnum> settledLCMEvents = Arrays.asList(LCMEventTypeEnum.EXERCISE, LCMEventTypeEnum.UNWIND,
                LCMEventTypeEnum.EXPIRATION);
        List<TradeDTO> trades = tradeService.searchByProductTypesAndNotInLcmEvents(productTypes, settledLCMEvents);
        trades = filterTradeByReadableBook(trades);

        int start = page * pageSize;
        int end = Math.min(start + pageSize, trades.size());
        return new RpcResponseListPaged<>(trades.subList(start, end), trades.size());
    }

    @BctMethodInfo(
            description = "??????Trade??????????????????????????????",
            retDescription = "Trade??????????????????????????????",
            retName = "tradeReferenceDTO",
            returnClass = TradeReferenceDTO.class,
            service = "trade-service"
    )
    public TradeReferenceDTO tradeReferenceGet() {
        TradeReferenceDTO ref = new TradeReferenceDTO();
        // only return supported configurations
        ref.setAssetClasses(Arrays.asList(InstrumentAssetClassTypeEnum.EQUITY));
        ref.setProductTypes(Arrays.asList(ProductTypeEnum.VANILLA_EUROPEAN));
        ref.setOptionTypes(Lists.newArrayList(OptionTypeEnum.values()));
        ref.setUnitTypes(Lists.newArrayList((UnitTypeEnum.values())));
        ref.setLcmEventTypes(Lists.newArrayList(LCMEventTypeEnum.values()));
        ref.setBusinessCenters(Lists.newArrayList(BusinessCenterEnum.values()));
        return ref;
    }

    @BctMethodInfo(
            description = "?????????????????????????????????",
            retDescription = "????????????????????????????????????",
            service = "trade-service")
    public Boolean allProductParameters(
            @BctMethodArg(description = "??????", argClass = AnnualizedAsianOptionDTO.class) Map<String, Object> asianOption,
            @BctMethodArg(description = "AutoCall", argClass = AnnualizedAutoCallOptionDTO.class) Map<String, Object> autoCallOption,
            @BctMethodArg(description = "?????????", argClass = AnnualizedAutoCallPhoenixOptionDTO.class) Map<String, Object> autoCallPhoenixOption,
            @BctMethodArg(description = "????????????", argClass = AnnualizedConcavaConvexOptionDTO.class) Map<String, Object> concavaConvexOption,
            @BctMethodArg(description = "????????????", argClass = AnnualizedDigitalOptionDTO.class) Map<String, Object> digitalOption,
            @BctMethodArg(description = "????????????", argClass = AnnualizedDoubleDigitalOptionDTO.class) Map<String, Object> doubleDigitalOption,
            @BctMethodArg(description = "??????", argClass = AnnualizedDoubleSharkFinOptionDTO.class) Map<String, Object> doubleSharkFinOption,
            @BctMethodArg(description = "?????????", argClass = AnnualizedDoubleTouchOptionDTO.class) Map<String, Object> doubleTouchOption,
            @BctMethodArg(description = "??????", argClass = AnnualizedEagleOptionDTO.class) Map<String, Object> eagleOption,
            @BctMethodArg(description = "??????", argClass = AnnualizedKnockOutOptionDTO.class) Map<String, Object> knockOutOption,
            @BctMethodArg(description = "ModelXY", argClass = AnnualizedModelXYOptionDTO.class) Map<String, Object> modelXYOption,
            @BctMethodArg(description = "????????????", argClass = AnnualizedRangeAccrualsOptionDTO.class) Map<String, Object> rangeAccrualsOption,
            @BctMethodArg(description = "??????", argClass = AnnualizedStraddleOptionDTO.class) Map<String, Object> straddleOption,
            @BctMethodArg(description = "????????????", argClass = AnnualizedTripleDigitalOptionDTO.class) Map<String, Object> tripleDigitalOption,
            @BctMethodArg(description = "??????", argClass = AnnualizedVanillaOptionDTO.class) Map<String, Object> vanillaOption,
            @BctMethodArg(description = "??????", argClass = AnnualizedVerticalSpreadOptionDTO.class) Map<String, Object> verticalSpreadOption,
            @BctMethodArg(description = "?????????", argClass = CashFlowDTO.class) Map<String, Object> cashFlow,
            @BctMethodArg(description = "??????", argClass = ForwardDTO.class) Map<String, Object> forward) {
        return true;
    }


    private void validateBook(String bookName) {
        if (StringUtils.isBlank(bookName)) throw new IllegalArgumentException(String.format("?????????????????????????????????bookName"));
        Collection<ResourceDTO> resources = resourceService.authBookGetCanRead();
        Boolean valid = resources.stream()
                .anyMatch(resourceDTO -> bookName.equals(resourceDTO.getResourceName()));
        if (!valid) {
            throw new IllegalArgumentException(String.format("???????????????:%s,???????????????", bookName));
        }
    }

    private List<String> filterReadableBookTradeIdByIndex(List<TradePositionIndexDTO> indexList) {
        if (CollectionUtils.isEmpty(indexList)) {
            return Lists.newArrayList();
        }
        List<String> bookNames = indexList.stream().map(TradePositionIndexDTO::getBookName).distinct().collect(Collectors.toList());
        List<Boolean> booleans = resourcePermissionService.authCan(BOOK.name(), bookNames, READ_TRADE.name());
        List<String> names = new ArrayList<>();
        //?????????????????????????????????
        for (int i = 0; i < booleans.size(); i++) {
            if (booleans.get(i)) {
                names.add(bookNames.get(i));
            }
        }
        return indexList
                .stream()
                .filter(i -> names.contains(i.getBookName()))
                .map(TradePositionIndexDTO::getTradeId)
                .distinct()
                .collect(Collectors.toList());
    }

    private Tuple2<List<TradeDTO>, Integer> filterAndFindPortfolioNames(
            List<String> portfolioNames, List<String> tradeIds, Integer page, Integer pageSize) {

        if (CollectionUtils.isNotEmpty(portfolioNames)) {
            List<String> portfolioTradeIds = portfolioSearchService.listTradeIdsByPortfolioNames(portfolioNames);
            tradeIds = tradeIds.stream().filter(tradeId -> portfolioTradeIds.contains(tradeId)).collect(Collectors.toList());
        }

        int totalSize = tradeIds.size();

        if (!ObjectUtils.isEmpty(page) && !ObjectUtils.isEmpty(pageSize)) {
            int start = page * pageSize;
            int end = Math.min(start + pageSize, tradeIds.size());
            tradeIds = tradeIds.subList(start, end);
        }

        List<TradeDTO> tradeDtos = tradeService.findByTradeIds(tradeIds);
        tradeDtos.forEach(t -> t.setPortfolioNames(portfolioSearchService.listPortfolioNamesByTradeId(t.getTradeId())));

        return new Tuple2<>(tradeDtos, totalSize);
    }

    private List<TradeDTO> filterTradeByReadableBook(List<TradeDTO> trades) {
        if (CollectionUtils.isEmpty(trades)) {
            return Lists.newArrayList();
        }

        List<String> bookNames = trades.stream().map(TradeDTO::getBookName).distinct().collect(Collectors.toList());
        List<Boolean> booleans = resourcePermissionService.authCan(BOOK.name(), bookNames, READ_TRADE.name());
        List<String> names = new ArrayList<>();
        //?????????????????????????????????
        for (int i = 0; i < booleans.size(); i++) {
            if (booleans.get(i)) {
                names.add(bookNames.get(i));
            }
        }

        return trades.stream().filter(t -> names.contains(t.getBookName())).collect(Collectors.toList());
    }

    private List<TradeDTO> filterAndSetPortfolioNames(List<String> portfolioNames, List<TradeDTO> trades) {
        List<String> tradeIds = CollectionUtils.isNotEmpty(portfolioNames) ?
                portfolioSearchService.listTradeIdsByPortfolioNames(portfolioNames) : Lists.newArrayList();
        Map<String, List<String>> portfolioTrades = portfolioSearchService.listAllPortfolioTrades(true);
        return trades.parallelStream()
                .filter(t -> CollectionUtils.isEmpty(portfolioNames) || tradeIds.contains(t.getTradeId()))
                .peek(t -> t.setPortfolioNames(portfolioTrades.getOrDefault(t.getTradeId(), Lists.newArrayListWithCapacity(0))))
                .collect(Collectors.toList());
    }

}
