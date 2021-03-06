package tech.tongyu.bct.exchange.api;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.reader.UnicodeReader;
import tech.tongyu.bct.auth.dto.ResourceDTO;
import tech.tongyu.bct.auth.enums.ResourcePermissionTypeEnum;
import tech.tongyu.bct.auth.enums.ResourceTypeEnum;
import tech.tongyu.bct.auth.service.ResourcePermissionService;
import tech.tongyu.bct.auth.service.ResourceService;
import tech.tongyu.bct.cm.product.iov.InstrumentOfValuePartyRoleTypeEnum;
import tech.tongyu.bct.common.api.annotation.BctApiTagEnum;
import tech.tongyu.bct.common.api.annotation.BctExcelTypeEnum;
import tech.tongyu.bct.common.api.annotation.BctMethodArg;
import tech.tongyu.bct.common.api.annotation.BctMethodInfo;
import tech.tongyu.bct.common.api.response.RpcResponseWithDiagnostics;
import tech.tongyu.bct.common.exception.CustomException;
import tech.tongyu.bct.common.util.CollectionUtils;
import tech.tongyu.bct.common.util.DateTimeUtils;
import tech.tongyu.bct.common.util.JsonUtils;
import tech.tongyu.bct.exchange.dto.*;
import tech.tongyu.bct.exchange.service.ExchangePortfolioService;
import tech.tongyu.bct.exchange.service.ExchangeService;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ExchangeApis {
    private ExchangeService exchangeService;
    private ResourceService resourceService;
    private ExchangePortfolioService exchangePortfolioService;
    private ResourcePermissionService resourcePermissionService;

    @Autowired
    public ExchangeApis(ExchangeService exchangeService,
                        ResourceService resourceService,
                        ExchangePortfolioService exchangePortfolioService,
                        ResourcePermissionService resourcePermissionService) {
        this.exchangeService = exchangeService;
        this.resourceService = resourceService;
        this.exchangePortfolioService = exchangePortfolioService;
        this.resourcePermissionService = resourcePermissionService;
    }

    private static Logger logger = LoggerFactory.getLogger(ExchangeApis.class);

    @BctMethodInfo(
            description = "??????????????????????????????",
            retDescription = "???????????????????????????????????????",
            retName = "positionRecordDTO",
            returnClass = PositionRecordDTO.class,
            service = "exchange-service"
    )
    public PositionRecordDTO excPositionRecordSave(
            @BctMethodArg(description = "?????????ID") String bookId,
            @BctMethodArg(description = "?????????ID") String instrumentId,
            @BctMethodArg(description = "????????????") Number longPosition,
            @BctMethodArg(description = "????????????") Number shortPosition,
            @BctMethodArg(description = "?????????") Number netPosition,
            @BctMethodArg(description = "??????") Number totalSell,
            @BctMethodArg(description = "??????") Number totalBuy,
            @BctMethodArg(description = "???????????????") Number historyBuyAmount,
            @BctMethodArg(description = "???????????????") Number historySellAmount,
            @BctMethodArg(description = "??????") Number marketValue,
            @BctMethodArg(description = "?????????") Number totalPnl,
            @BctMethodArg(description = "????????????") String dealDate) {
        Set<String> bookNames = resourceService.authBookGetCanRead().stream().map(ResourceDTO::getResourceName).collect(Collectors.toSet());
        if (!bookNames.contains(bookId)) {
            throw new CustomException("??????????????????????????????" + bookId);
        }
        LocalDate parsedSearchDate = DateTimeUtils.parseToLocalDate(dealDate);
        PositionRecordDTO positionRecordDTO = new PositionRecordDTO(
                bookId, instrumentId,
                new BigDecimal(longPosition.toString()),
                new BigDecimal(shortPosition.toString()),
                new BigDecimal(netPosition.toString()),
                new BigDecimal(totalSell.toString()),
                new BigDecimal(totalBuy.toString()),
                new BigDecimal(historyBuyAmount.toString()),
                new BigDecimal(historySellAmount.toString()),
                new BigDecimal(marketValue.toString()),
                new BigDecimal(totalPnl.toString())
                , parsedSearchDate);
        return exchangeService.savePosition(positionRecordDTO);
    }

    @BctMethodInfo(
            description = "????????????????????????????????????",
            retDescription = "?????????????????????????????????????????????",
            retName = "List of PositionRecordDTO",
            returnClass = PositionRecordDTO.class,
            service = "exchange-service"
    )
    public List<PositionRecordDTO> excPositionRecordSearch(
            @BctMethodArg(description = "????????????") String searchDate
    ) {
        Set<String> bookNames = resourceService.authBookGetCanRead().stream().map(ResourceDTO::getResourceName).collect(Collectors.toSet());
        LocalDate parsedSearchDate = DateTimeUtils.parseToLocalDate(searchDate);
        return exchangeService.searchPositionRecord(parsedSearchDate).stream()
                .filter(v -> bookNames.contains(v.getBookId())).collect(Collectors.toList());
    }

    @BctMethodInfo(
            description = "???????????????????????????????????????(??????????????????)",
            retDescription = "??????????????????????????????????????????????????????(??????????????????)",
            retName = "List of PositionRecordDTO",
            returnClass = PositionRecordDTO.class,
            service = "exchange-service"
    )
    public List<PositionRecordDTO> excPositionRecordSearchGroupByInstrumentId(
            @BctMethodArg(description = "????????????") String searchDate
    ) {
        Set<String> bookNames = resourceService.authBookGetCanRead().stream().map(ResourceDTO::getResourceName).collect(Collectors.toSet());
        LocalDate parsedSearchDate = DateTimeUtils.parseToLocalDate(searchDate);
        return exchangeService.searchPositionRecordGroupByInstrumentId(parsedSearchDate, bookNames);
    }

    @BctMethodInfo(
            description = "????????????????????????????????????",
            retDescription = "??????????????????????????????",
            retName = "List of PositionPortfolioRecordDTO",
            returnClass = PositionPortfolioRecordDTO.class,
            service = "exchange-service"
    )
    public List<PositionPortfolioRecordDTO> excGroupedPositionRecordSearch(
            @BctMethodArg(required = false, description = "????????????????????????") List<String> portfolioNames,
            @BctMethodArg(required = false, description = "???????????????") List<String> books,
            @BctMethodArg(required = false, description = "????????????") String searchDate) {
        List<String> readableBooks = resourceService.authBookGetCanRead().stream()
                .map(ResourceDTO::getResourceName).distinct().collect(Collectors.toList());
        return exchangeService.searchGroupedPositionRecord(portfolioNames, books, searchDate, readableBooks);
    }

    @BctMethodInfo(
            description = "??????????????????????????????",
            retDescription = "????????????????????????",
            retName = "List of PositionSnapshotDTO",
            returnClass = PositionSnapshotDTO.class,
            service = "exchange-service"
    )
    public List<PositionSnapshotDTO> excPositionSnapshotListAll() {
        Set<String> bookNames = resourceService.authBookGetCanRead().stream().map(ResourceDTO::getResourceName).collect(Collectors.toSet());
        return exchangeService.findAllPositionSnapshot()
                .stream().filter(v -> bookNames.contains(v.getBookId())).collect(Collectors.toList());
    }

    @BctMethodInfo(
            description = "?????????????????????????????????????????????????????????",
            retDescription = "???????????????????????????????????????????????????",
            retName = "List of Instrument IDs",
            service = "exchange-service"
    )
    public List<String> excListAllInstrumentsInTradeRecords(
            @BctMethodArg(required = false, description = "??????") String criteria
    ) {
        return exchangeService.fuzzyQueryInstrumentsInTradeRecords(criteria);
    }

    @BctMethodInfo(
            description = "??????????????????????????????",
            retDescription = "????????????????????????",
            retName = "List of trade info",
            service = "exchange-service",
            tags = {BctApiTagEnum.Excel},
            excelType = BctExcelTypeEnum.Table,
            returnClass = PositionSnapshotDTO.class
    )
    public List<Map<String, Object>> excPositionSnapshotMapListAll() {
        Set<String> bookNames = resourceService.authBookGetCanRead().stream().map(ResourceDTO::getResourceName).collect(Collectors.toSet());
        List<PositionSnapshotDTO> dtoList = exchangeService.findAllPositionSnapshot()
                .stream().filter(v -> bookNames.contains(v.getBookId())).collect(Collectors.toList());
        return dtoList.stream()
                .map(dto -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("bookId", dto.getBookId());
                    map.put("instrumentId", dto.getInstrumentId());
                    map.put("longPosition", dto.getLongPosition().doubleValue());
                    map.put("shortPosition", dto.getShortPosition().doubleValue());
                    map.put("netPosition", dto.getNetPosition().doubleValue());
                    map.put("historyBuyAmount", dto.getHistoryBuyAmount().doubleValue());
                    map.put("historySellAmount", dto.getHistorySellAmount().doubleValue());
                    map.put("totalPnl", dto.getTotalPnl().doubleValue());
                    return map;
                })
                .collect(Collectors.toList());
    }

    @BctMethodInfo(
            description = "??????????????????????????????(??????????????????)",
            retDescription = "????????????????????????(??????????????????)",
            retName = "List of PositionSnapshotDTO",
            returnClass = PositionSnapshotDTO.class,
            service = "exchange-service"
    )
    public List<PositionSnapshotDTO> excPositionSnapshotListAllGroupByInstrumentId() {
        return exchangeService.findPositionSnapshotGroupByInstrumentId();
    }

    @BctMethodInfo(
            description = "??????????????????",
            retDescription = "???????????????????????????",
            retName = "TradeRecordDTO",
            returnClass = TradeRecordDTO.class,
            service = "exchange-service"
    )
    public TradeRecordDTO exeTradeRecordSave(
            @BctMethodArg(description = "?????????ID") String bookId,
            @BctMethodArg(description = "??????ID") String tradeId,
            @BctMethodArg(description = "????????????") String tradeAccount,
            @BctMethodArg(description = "?????????ID") String instrumentId,
            @BctMethodArg(description = "????????????") Number multiplier,
            @BctMethodArg(description = "????????????") Number dealAmount,
            @BctMethodArg(description = "????????????") Number dealPrice,
            @BctMethodArg(description = "????????????") String dealTime,
            @BctMethodArg(description = "???/???", argClass = OpenCloseEnum.class) String openClose,
            @BctMethodArg(description = "????????????", argClass = InstrumentOfValuePartyRoleTypeEnum.class) String direction,
            @BctMethodArg(required = false, description = "????????????????????????") List<String> portfolioNames
    ) {
        //make sure all portfolioNames are existed
        if (!CollectionUtils.isEmpty(portfolioNames)) {
            portfolioNames.forEach(portfolioName -> {
                if (!exchangePortfolioService.existsByPortfolioName(portfolioName)) {
                    throw new CustomException(String.format("????????????:[%s]?????????", portfolioName));
                }
            });
        }

        TradeRecordDTO tradeRecordDto = new TradeRecordDTO(bookId, tradeId, tradeAccount, instrumentId,
                new BigDecimal(multiplier.toString()),
                new BigDecimal(dealAmount.toString()),
                new BigDecimal(dealPrice.toString()),
                StringUtils.isBlank(dealTime) ? null : LocalDateTime.parse(dealTime),
                StringUtils.isBlank(openClose) ? null : OpenCloseEnum.valueOf(openClose),
                StringUtils.isBlank(direction) ? null : InstrumentOfValuePartyRoleTypeEnum.valueOf(direction));
        Optional<TradeRecordDTO> tradeRecord = exchangeService.findTradeRecordByTradeId(tradeId);
        if (tradeRecord.isPresent()) {
            throw new CustomException("????????????????????????:" + tradeId);
        }
        TradeRecordDTO tradeRecordDTO = exchangeService.saveTradeRecordWithoutNewTransaction(tradeRecordDto);
        exchangeService.savePositionSnapshotByTradeRecords(Arrays.asList(tradeRecordDTO));
        if (!CollectionUtils.isEmpty(portfolioNames)) {
            portfolioNames.forEach(name -> exchangePortfolioService.createExchangeTradePortfolioWithoutNewTransaction(tradeId, name));
        }
        tradeRecordDTO.setPortfolioNames(portfolioNames);
        return tradeRecordDTO;
    }

    @BctMethodInfo(
            description = "????????????????????????",
            retDescription = "??????????????????",
            retName = "true or false",
            service = "exchange-service"
    )
    public Boolean exeTradeRecordSaveAll(
            @BctMethodArg(description = "??????????????????", argClass = TradeRecordDTO.class) List<Map<String, Object>> tradeRecords
    ) {
        List<TradeRecordDTO> tradeExist = new ArrayList<>();
        List<TradeRecordDTO> tradeNoExist = new ArrayList<>();
        tradeRecords.forEach(
                tradeRecord -> {
                    TradeRecordDTO tradeRecordDto = JsonUtils.mapper.convertValue(tradeRecord, TradeRecordDTO.class);
                    Optional<TradeRecordDTO> trade = exchangeService.findTradeRecordByTradeId(tradeRecordDto.getTradeId());
                    if (trade.isPresent()) {
                        tradeExist.add(tradeRecordDto);
                    } else {
                        tradeNoExist.add(tradeRecordDto);
                    }
                }
        );
        List<TradeRecordDTO> tradeRecordNoExistList = tradeNoExist.stream().
                map(exchangeService::saveTradeRecordWithoutNewTransaction).collect(Collectors.toList());
        exchangeService.savePositionSnapshotByTradeRecords(tradeRecordNoExistList);
        if (tradeExist.size() != 0) {
            List<String> tradeIds = tradeExist.stream().map(TradeRecordDTO::getTradeId).collect(Collectors.toList());
            throw new CustomException("????????????????????????:" + tradeIds.toString());
        }
        return true;
    }

    @BctMethodInfo(
            description = "????????????Id??????????????????",
            retDescription = "????????????",
            retName = "Optional<TradeRecordDTO>",
            returnClass = TradeRecordDTO.class,
            service = "exchange-service"
    )
    public Optional<TradeRecordDTO> exeTradeRecordSearchByTradeId(
            @BctMethodArg(description = "??????ID") String tradeId
    ) {
        if (StringUtils.isEmpty(tradeId)) {
            throw new CustomException("tradeId????????????");
        }
        Optional<TradeRecordDTO> tradeRecordDTO = exchangeService.findTradeRecordByTradeId(tradeId);
        return tradeRecordDTO;
    }

    @BctMethodInfo(
            description = "??????????????????",
            retDescription = "???????????????????????????",
            retName = "List of TradeRecordDTO",
            returnClass = TradeRecordDTO.class,
            service = "exchange-service"
    )
    public List<TradeRecordDTO> excTradeRecordSearch(
            @BctMethodArg(required = false, description = "?????????ID??????") List<String> instrumentIds,
            @BctMethodArg(description = "????????????") String startTime,
            @BctMethodArg(description = "????????????") String endTime
    ) {
        LocalDateTime startDateTime = StringUtils.isBlank(startTime) ? null : LocalDateTime.parse(startTime);
        LocalDateTime endDateTime = StringUtils.isBlank(endTime) ? null : LocalDateTime.parse(endTime);

        Set<String> bookNames = resourceService.authBookGetCanRead().stream().map(ResourceDTO::getResourceName).collect(Collectors.toSet());
        return exchangeService.searchTradeRecord(instrumentIds, startDateTime, endDateTime)
                .stream().filter(v -> bookNames.contains(v.getBookId())).collect(Collectors.toList());
    }

    @BctMethodInfo(
            description = "?????????????????????????????????",
            retDescription = "????????????????????????????????????????????????????????????????????????",
            retName = "List of TradeRecordDTOs and Diagnostics",
            returnClass = TradeRecordDTO.class,
            service = "exchange-service"
    )
    @Transactional
    public RpcResponseWithDiagnostics<List<TradeRecordDTO>, List<String>> exeTradeRecordUpload(
            @BctMethodArg(description = "????????????") MultipartFile file
    ) throws IllegalStateException {
        BufferedReader buffer;
        try {
            if (file.isEmpty()) {
                throw new IllegalArgumentException("??????????????????");
            }
            buffer = new BufferedReader(new UnicodeReader(file.getInputStream()));
            logger.info(String.format("start backFilling tradeRecord from file (size=%s)", file.getSize()));

            Stream<String> linesFiltered = buffer.lines().map(String::trim).filter(StringUtils::isNotEmpty);
            return backFillTradeReport(linesFiltered);
        } catch (IOException e) {
            logger.error("failed to call -> uploadTradeRecordList message ={} parameter ={} ", e.getMessage(), file.getName());
            throw new IllegalStateException("??????????????????");
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage());
        }

    }

    @BctMethodInfo(
            description = "?????????????????????????????????",
            retDescription = "??????????????????",
            retName = "true or false",
            service = "exchange-service"
    )
    public Boolean exeTradePortfolioCreate(
            @BctMethodArg(description = "??????ID") String tradeId,
            @BctMethodArg(description = "??????????????????") String portfolioName
    ) {
        return exchangePortfolioService.createExchangeTradePortfolioWithoutNewTransaction(tradeId, portfolioName);
    }

    @BctMethodInfo(
            description = "???????????????????????????????????????",
            retDescription = "????????????????????????",
            retName = "true or false",
            service = "exchange-service"
    )
    public Boolean exeTradePortfolioCreateBatch(
            @BctMethodArg(description = "??????ID") String tradeId,
            @BctMethodArg(description = "????????????????????????") List<String> portfolioNames
    ) {
        if (StringUtils.isBlank(tradeId)) {
            throw new IllegalArgumentException("?????????????????????tradeId");
        }
        if (CollectionUtils.isEmpty(portfolioNames)) {
            throw new IllegalArgumentException("???????????????????????????portfolioName");
        }
        return exchangePortfolioService.createExchangeTradePortfolioBatch(tradeId, portfolioNames);
    }

    @BctMethodInfo(
            description = "???????????????????????????????????????????????????",
            retDescription = "????????????????????????",
            retName = "true or false",
            service = "exchange-service"
    )
    public Boolean exeTradePortfolioDelete(
            @BctMethodArg(description = "??????ID") String tradeId,
            @BctMethodArg(description = "??????????????????") String portfolioName
    ) {
        return exchangePortfolioService.deleteExchangeTradePortfolio(tradeId, portfolioName);
    }

    @BctMethodInfo(
            description = "???????????????????????????????????????(??????????????????????????????????????????????????????????????????????????????)",
            retDescription = "??????????????????",
            retName = "true or false",
            service = "exchange-service"
    )
    public Boolean exeTradePortfolioRefresh(
            @BctMethodArg(description = "??????ID??????") List<String> tradeIds,
            @BctMethodArg(description = "????????????????????????") List<String> portfolioNames
    ) {
        return exchangePortfolioService.refreshExchangeTradePortfolios(tradeIds, portfolioNames);
    }

    @BctMethodInfo(
            description = "?????????????????????????????????????????????",
            retDescription = "???????????????????????????????????????",
            retName = "all trades and their portfolios",
            service = "exchange-service"
    )
    public Map<String, List<String>> exePortfolioTradesList() {
        return exchangePortfolioService.listAllExchangePortfolioTrades();
    }

    private RpcResponseWithDiagnostics<List<TradeRecordDTO>, List<String>> backFillTradeReport(Stream<String> lines) {
        List<String> bookNames = resourceService.authBookGetCanRead().stream().map(ResourceDTO::getResourceName).collect(Collectors.toList());
        List<Boolean> writable = resourcePermissionService.authCan(ResourceTypeEnum.BOOK.name(), bookNames, ResourcePermissionTypeEnum.UPDATE_BOOK.name());
        for (int i = 0; i < bookNames.size(); i++) {
            if (!writable.get(i)) {
                bookNames.set(i, null);
            }
        }
        List<String> filteredBookNames = bookNames.stream().filter(StringUtils::isNotBlank).collect(Collectors.toList());
        List<TradeRecordDTO> success = new LinkedList<>();
        List<String> failure = new LinkedList<>();
        lines.forEach(line -> {
            String[] columns = line.split(",");
            if (columns.length < 3) {
                failure.add(line);
                return;
            }
            String tradeId = columns[2];
            try {
                TradeRecordDTO result = parseAndSaveTradeRecordList(columns, filteredBookNames);
                success.add(result);
            } catch (Exception e) {
                failure.add(String.format("%s`%s", tradeId, e.getMessage()));
            }
        });
        if (success.size() > 0) {
            exchangeService.savePositionSnapshotByTradeRecords(success);
        }

        return new RpcResponseWithDiagnostics() {

            @Override
            public Object getResult() {
                return success;
            }

            @Override
            public Object getDiagnostics() {
                return failure;
            }
        };
    }

    private TradeRecordDTO parseAndSaveTradeRecordList(String[] columns, List<String> writableBooks) {
        if (columns.length < 10) {
            throw new CustomException("?????????????????????????????????");
        }
        int i = 0;
        TradeRecordDTO tradeRecordDto = new TradeRecordDTO();
        tradeRecordDto.setBookId(columns[i++].trim());
        if (!writableBooks.contains(tradeRecordDto.getBookId())) {
            throw new CustomException(String.format("?????????????????????????????????:{%s}", tradeRecordDto.getBookId()));
        }
        List<String> portfolioNames = Stream.of(columns[i++].split("\\|")).map(String::trim).collect(Collectors.toList());
        tradeRecordDto.setTradeId(columns[i++].trim());
        exchangeService.findTradeRecordByTradeId(tradeRecordDto.getTradeId()).ifPresent(v -> {
            throw new CustomException("??????ID??????");
        });
        tradeRecordDto.setTradeAccount(columns[i++].trim());
        tradeRecordDto.setInstrumentId(columns[i++].trim());
        String dealAmount = columns[i++].trim();
        String dealPrice = columns[i++].trim();
        String openClose = columns[i++].trim();
        String direction = columns[i++].trim();
        String dealTime = columns[i].trim();
        try {
            tradeRecordDto.setDealAmount(new BigDecimal(dealAmount));
        } catch (NumberFormatException e) {
            throw new CustomException(String.format("????????????????????????:{%s}", dealAmount));
        }
        try {
            tradeRecordDto.setDirection(InstrumentOfValuePartyRoleTypeEnum.valueOf(direction));
        } catch (IllegalArgumentException e) {
            throw new CustomException(String.format("???/???????????????[%s]?????????????????????{%s}", Arrays.toString(InstrumentOfValuePartyRoleTypeEnum.values()), direction));
        }
        try {
            tradeRecordDto.setDealPrice(new BigDecimal(dealPrice));
            tradeRecordDto.setOpenClose(OpenCloseEnum.valueOf(openClose));
            tradeRecordDto.setDealTime(LocalDateTime.parse(dealTime));
        } catch (NumberFormatException e) {
            throw new CustomException(String.format("??????????????????:{%s}", dealPrice));
        } catch (IllegalArgumentException e) {
            throw new CustomException(String.format("???/???????????????[%s]???????????????{%s}", Arrays.toString(OpenCloseEnum.values()), openClose));
        } catch (DateTimeParseException e) {
            throw new CustomException(String.format("???????????????????????????{%s}", dealTime));
        }
        TradeRecordDTO saved = exchangeService.saveTradeRecordWithNewTransaction(tradeRecordDto);

        portfolioNames.stream().filter(str -> StringUtils.isNotBlank(str))
                .forEach(portfolioName -> exchangePortfolioService
                        .createExchangeTradePortfolioWithNewTransaction(columns[2], portfolioName));
        return saved;
    }
}
