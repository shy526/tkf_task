package com.github.shy526.task;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.shy526.http.HttpClientService;
import com.github.shy526.http.HttpHelp;
import com.github.shy526.http.HttpResult;
import lombok.Data;
import lombok.extern.java.Log;
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

public class CraftsTopTask implements Task {

    private final static String GET_ITEM_URL_FORMAT = "https://tarkov-market.com/api/be/items?lang=cn&search=%s&tag=&sort=change24&sort_direction=desc&trader=&skip=0&limit=20";
    private final static String GET_RECIPES_URL_FORMAT = "https://tarkov-market.com/api/be/hideout";


    @Override
    public void run() {
        long l = System.currentTimeMillis();
        HttpClientService httpClientService = HttpHelp.getInstance();
        HttpResult httpResult = httpClientService.get(GET_RECIPES_URL_FORMAT);
        String entityStr = httpResult.getEntityStr();
        JSONObject result = JSON.parseObject(entityStr);
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
                    Item output = recipe.getOutput();
                    JSONObject outItem = getItemInfoByUid(output.getUid());
                    List<Price> sellPrices = JSON.parseArray(outItem.getString("sellPrices"), Price.class);
                    Price sellMaxPrice = sellPrices.stream().max(Comparator.comparing(Price::getPrice)).get();
                    BigDecimal totalCellPrice = sellMaxPrice.getPrice().multiply(BigDecimal.valueOf(output.amount));
                    Price price = new Price();
                    price.setPrice(totalCellPrice);
                    price.setType(sellMaxPrice.getType());
                    BigDecimal totalBuyPrice = BigDecimal.ZERO;
                    for (Item input : recipe.getInput()) {
                        JSONObject inItem = getItemInfoByUid(input.getUid());
                        List<Price> buyPrices = JSON.parseArray(inItem.getString("buyPrices"), Price.class);
                        Price buyMinPrice = buyPrices.stream().min(Comparator.comparing(Price::getPrice)).get();
                        totalBuyPrice = totalBuyPrice.add(buyMinPrice.getPrice().multiply(BigDecimal.valueOf(input.amount)));
                    }
                    BigDecimal profit = totalCellPrice.subtract(totalBuyPrice);
                    if (BigDecimal.ZERO.compareTo(profit) >= 0) {
                        continue;
                    }
                    Long craftTime = recipe.getCraftTime();
                    BigDecimal timeProfit = profit.divide(BigDecimal.valueOf(craftTime / 60f / 60), 2, RoundingMode.HALF_UP);
                    recipe.setTimeProfit(timeProfit);
                    recipe.setProfit(profit);
                    System.out.println(recipe.getUid() + ":" + profit + "    " + timeProfit + "/h" + "  " + craftTime / 60f / 60);
                }
                resultMap.put(item.getKey() + "-" + lvItems, lvItems.getValue());
            }
        }
        System.out.println(System.currentTimeMillis() - l);
        System.out.println("resultMap = " + resultMap);
    }


    private JSONObject getItemInfoByUid(String uid) {
        return getItem(uid, uid);
    }

    private JSONObject getItem(String search, String uid) {
        HttpClientService httpClientService = HttpHelp.getInstance();
        String url = String.format(GET_ITEM_URL_FORMAT, new String(URLCodec.encodeUrl(null, search.getBytes())));
        System.out.println("url = " + url);
        HttpResult httpResult = httpClientService.get(url);
        Integer httpStatus = httpResult.getHttpStatus();
        if (httpStatus.equals(429)) {
            try {
                TimeUnit.SECONDS.sleep(1);
                getItem(search, uid);
            } catch (InterruptedException e) {
            }
        }
        System.out.println("getHttpStatus = " +httpStatus);

        JSONObject jsonObject = JSON.parseObject(httpResult.getEntityStr());
        JSONArray items = JSON.parseArray(deCodeString(jsonObject, "items"));
        return (JSONObject) items.stream().filter(item -> uid.equals(((JSONObject) item).getString("uid"))).findAny().get();
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

        private BigDecimal profit;
        private BigDecimal TimeProfit;
    }

    @Data
    public static class Item implements Serializable {
        private String shortName;
        private String uid;
        private String type;
        private Integer amount;

    }

    @Data
    public static class Price {
        private String type;
        private BigDecimal price;
    }
}
