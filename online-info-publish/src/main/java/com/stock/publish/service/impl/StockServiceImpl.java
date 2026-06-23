package com.stock.publish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stock.publish.entity.SyncStockInfo;
import com.stock.publish.mapper.SyncStockInfoMapper;
import com.stock.publish.service.StockService;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class StockServiceImpl implements StockService {

    private final SyncStockInfoMapper stockInfoMapper;
    private final RestTemplate restTemplate;

    @Value("${subsystems.central-trade.base-url:http://localhost:8082}")
    private String centralTradeBaseUrl;
    @Value("${subsystems.central-trade.stocks-path:/api/central-trading/stocks}")
    private String stocksPath;

    public StockServiceImpl(SyncStockInfoMapper stockInfoMapper, RestTemplate restTemplate) {
        this.stockInfoMapper = stockInfoMapper;
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void init() {
        syncFromCentralSystem();
    }

    /** 定时从中央交易系统重新同步股票字典（每 60 秒），使 CT 新增/修改的股票自动反映到本系统。 */
    @Scheduled(fixedRate = 60_000)
    public void scheduledSync() {
        syncFromCentralSystem();
    }

    @Override
    public List<SyncStockInfo> search(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return stockInfoMapper.selectList(null);
        }
        LambdaQueryWrapper<SyncStockInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(SyncStockInfo::getStockCode, keyword)
                .or()
                .like(SyncStockInfo::getStockName, keyword)
                .or()
                .like(SyncStockInfo::getPinyinAbbr, keyword)
                .last("LIMIT 10");
        return stockInfoMapper.selectList(wrapper);
    }

    @Override
    public SyncStockInfo getByCode(String stockCode) {
        // DONE: 根据股票代码查询
        return stockInfoMapper.selectById(stockCode);
    }

    @Override
    public void syncFromCentralSystem() {
        try {
            String url = centralTradeBaseUrl + stocksPath;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stocks = restTemplate.getForObject(url, List.class);
            if (stocks == null || stocks.isEmpty()) return;

            for (Map<String, Object> s : stocks) {
                String code = String.valueOf(s.getOrDefault("stockCode", ""));
                if (code.isEmpty()) continue;

                SyncStockInfo info = new SyncStockInfo();
                info.setStockCode(code);
                info.setStockName(String.valueOf(s.getOrDefault("stockName", "")));
                info.setStockType(0);
                // CT API 返回字段名为 previousClose（非 yesterdayClose），两者均尝试兼容
                Object yc = s.get("previousClose");
                if (yc == null) yc = s.get("yesterdayClose");
                info.setYesterdayClose(yc != null ? new BigDecimal(yc.toString()) : BigDecimal.ZERO);
                info.setLimitRate(new BigDecimal("0.1000"));
                info.setStatus(0);
                info.setPinyinAbbr(toPinyinAbbr(info.getStockName()));

                // 已存在则更新，不存在则插入——支持 CT 端新增股票后定时同步自动感知
                SyncStockInfo existing = stockInfoMapper.selectById(code);
                if (existing != null) {
                    // 仅当 CT 返回有效昨收时才更新（避免 CT 字段缺失导致除零异常）
                    if (info.getYesterdayClose().compareTo(BigDecimal.ZERO) > 0) {
                        existing.setYesterdayClose(info.getYesterdayClose());
                    }
                    existing.setStockName(info.getStockName());
                    existing.setPinyinAbbr(info.getPinyinAbbr());
                    stockInfoMapper.updateById(existing);
                } else {
                    stockInfoMapper.insert(info);
                }
            }
        } catch (Exception e) {
            // 中央交易系统未就绪时保留 DB 已有数据
        }
    }

    /** 生成拼音首字母缩写，如 "贵州茅台" → "GZMT" */
    private String toPinyinAbbr(String chinese) {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.UPPERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);

        StringBuilder sb = new StringBuilder();
        for (char c : chinese.toCharArray()) {
            try {
                String[] pinyin = PinyinHelper.toHanyuPinyinStringArray(c, format);
                if (pinyin != null && pinyin.length > 0 && !pinyin[0].isEmpty()) {
                    sb.append(pinyin[0].charAt(0));
                }
            } catch (Exception ignored) {}
        }
        return sb.toString();
    }
}
