package com.github.shy526.task;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.shy526.config.Context;
import com.github.shy526.http.HttpClientService;
import com.github.shy526.http.HttpResult;
import com.github.shy526.service.GithubRestService;
import com.github.shy526.service.GithubRestServiceImpl;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.net.URLCodec;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class CraftsTopTask implements Task {
    private final static String GET_ITEM_URL_FORMAT = "https://tarkov-market.com/api/be/items?lang=cn&search=%s&tag=&sort=change24&sort_direction=desc&trader=&skip=%s";
    private final static String GET_RECIPES_URL_FORMAT = "https://tarkov-market.com/api/be/hideout";
    HttpClientService httpClientService = Context.getInstance(HttpClientService.class);
    GithubRestService githubRestService = Context.getInstance(GithubRestServiceImpl.class);

    @Override
    public void run() {
        long l = System.currentTimeMillis();
        JSONObject result = httpGetJsonObject(GET_RECIPES_URL_FORMAT);
        String recipesStr = deCodeString(result, "recipes");
        List<Recipe> recipes = JSON.parseArray(recipesStr, Recipe.class);
        //按设施分组
        Map<String, List<Recipe>> facilityGroup = recipes.stream().collect(Collectors.groupingBy(Recipe::getFacility));
        HashMap<String, List<Recipe>> resultMap = new HashMap<>();
        for (Map.Entry<String, List<Recipe>> item : facilityGroup.entrySet()) {
            List<Recipe> facility = item.getValue();
            //按等级分组
            Map<Integer, List<Recipe>> facilityLvGroup = facility.stream().collect(Collectors.groupingBy(Recipe::getLevel));
            for (Map.Entry<Integer, List<Recipe>> lvItems : facilityLvGroup.entrySet()) {
                for (Recipe recipe : lvItems.getValue()) {
                    //region 产出获取
                    Item output = recipe.getOutput();
                    JSONObject outItem = getItemInfoBy(output.getUid(), output.getUid());
                    output.setName(outItem.getString("name"));
                    List<Price> sellPrices = JSON.parseArray(outItem.getString("sellPrices"), Price.class);
                    Price sellMaxPrice = sellPrices.stream().max(Comparator.comparing(Price::getPrice)).get();
                    BigDecimal totalSellPrice = sellMaxPrice.getPrice().multiply(BigDecimal.valueOf(output.amount));
                    Price price = new Price();
                    price.setPrice(totalSellPrice);
                    price.setType(sellMaxPrice.getType());
                    output.setTotalPrice(price);
                    //endregion
                    BigDecimal totalBuyPrice = BigDecimal.ZERO;
                    //配方
                    for (Item input : recipe.getInput()) {
                        JSONObject inItem = getItemInfoBy(input.getUid(), input.getUid());
                        input.setName(inItem.getString("name"));
                        List<Price> buyPrices = JSON.parseArray(inItem.getString("buyPrices"), Price.class);
                        Price buyMinPrice = buyPrices.stream().min(Comparator.comparing(Price::getPrice)).get();
                        BigDecimal temp = buyMinPrice.getPrice().multiply(BigDecimal.valueOf(input.amount));
                        totalBuyPrice = totalBuyPrice.add(temp);
                        buyMinPrice.setPrice(temp);
                        input.setTotalPrice(buyMinPrice);
                    }
                    BigDecimal profit = totalSellPrice.subtract(totalBuyPrice);
                    if (BigDecimal.ZERO.compareTo(profit) >= 0) {
                        continue;
                    }
                    fullItemInfo(output);
                    for (Item input : recipe.getInput()) {
                        fullItemInfo(input);
                    }
                    Long craftTime = recipe.getCraftTime();
                    BigDecimal timeProfit = profit.divide(BigDecimal.valueOf(craftTime / 60f / 60), 2, RoundingMode.HALF_UP);
                    recipe.setTimeProfit(timeProfit);
                    recipe.setProfit(profit);
                    recipe.setSellPrice(totalSellPrice);
                    recipe.setBuyPrice(totalBuyPrice);
                }
                resultMap.put(item.getKey() + "-" + lvItems, lvItems.getValue());
            }
        }
/*        GithubVo githubVo = new GithubVo();
        githubRestService.createOrUpdateFile(githubVo);*/
        log.info("{}->end->runTime{}ms", this.getClass().getSimpleName(), System.currentTimeMillis() - l);

    }

    private JSONObject httpGetJsonObject(String url) {
        JSONObject result = null;
        try (HttpResult httpResult = httpClientService.get(url)) {
            String entityStr = httpResult.getEntityStr();
            result = JSON.parseObject(entityStr);
        } catch (Exception ignored) {
        }
        return result;
    }

    private void fullItemInfo(Item temp) {
        JSONObject o = getItemInfoBy(temp.getName(), temp.getUid());
        if (o.isEmpty()){
             o = getItem(temp.getName(), temp.getUid(), 4,20);
        }
        temp.setCnName(o.getString("cnName"));
        temp.setImg(o.getString("wikiIcon"));
    }


    private JSONObject getItemInfoBy(String search, String uid) {
        return getItem(search, uid, 4,0);
    }

    private JSONObject getItem(String search, String uid, Integer count,Integer skip) {
        String url = String.format(GET_ITEM_URL_FORMAT, new String(URLCodec.encodeUrl(null, search.getBytes())),skip.toString());
        JSONObject result = new JSONObject();
        try (HttpResult httpResult = httpClientService.get(url)) {
            Integer httpStatus = httpResult.getHttpStatus();
            if (httpStatus.equals(429)) {
                try {
                    count--;
                    if (count <= 0) {
                        return null;
                    }
                    TimeUnit.SECONDS.sleep(5);
                    result= getItem(search, uid, count,skip);

                } catch (Exception ignored) {
                }
            }
            JSONObject jsonObject = JSON.parseObject(httpResult.getEntityStr());
            JSONArray items = JSON.parseArray(deCodeString(jsonObject, "items"));
            result= (JSONObject) items.stream().filter(item -> uid.equals(((JSONObject) item).getString("uid"))).findAny().get();
        } catch (Exception ignored) {
        }
        return result;
    }

    public String deCodeString(JSONObject obj, String fieldName) {
        String metalFuel = obj.getString(fieldName);
        try {
            byte[] bytes = URLCodec.decodeUrl(Base64.decodeBase64(metalFuel.substring(0, 5) + metalFuel.substring(10)));
            return new String(bytes);
        } catch (Exception ignored) {
        }
        return null;
    }

    @Data
    public static class Recipe implements Serializable {
        private String uid;
        private String type;
        private Integer level;
        private Long craftTime;
        private String facility;
        private List<Item> input;
        private Item output;
        private BigDecimal buyPrice;
        private BigDecimal sellPrice;
        private BigDecimal profit;
        private BigDecimal TimeProfit;
    }

    @Data
    public static class Item implements Serializable {
        private String shortName;
        private String uid;
        private String type;
        private Integer amount;
        private String name;
        private String cnName;
        private Price totalPrice;
        private String img;

    }

    @Data
    public static class Price {
        private String type;
        private BigDecimal price;
    }
}
