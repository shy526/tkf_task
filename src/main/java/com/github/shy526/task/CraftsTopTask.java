package com.github.shy526.task;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.shy526.http.HttpClientService;
import com.github.shy526.http.HttpHelp;
import com.github.shy526.http.HttpResult;
import lombok.Data;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.net.URLCodec;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class CraftsTopTask implements Task {

    private final static String GET_ITEM_URL_FORMAT = "https://tarkov-market.com/api/be/items?lang=cn&search=%s&tag=&sort=change24&sort_direction=desc&trader=&skip=0&limit=20";
    private final static String GET_RECIPES_URL_FORMAT = "https://tarkov-market.com/api/be/hideout";


    @Override
    public void run() {
        HttpClientService httpClientService = HttpHelp.getInstance();
        HttpResult httpResult = httpClientService.get(GET_RECIPES_URL_FORMAT);
        String entityStr = httpResult.getEntityStr();
        JSONObject result = JSON.parseObject(entityStr);
        String recipesStr = deCodeString(result, "recipes");
        List<Recipe> recipes = JSON.parseArray(recipesStr, Recipe.class);
        Map<String, List<Recipe>> facilityGroup = recipes.stream().collect(Collectors.groupingBy(Recipe::getFacility));
        for (Map.Entry<String, List<Recipe>> item : facilityGroup.entrySet()) {
            List<Recipe> facility = item.getValue();
            Map<Integer, List<Recipe>> facilityLvGroup = facility.stream().collect(Collectors.groupingBy(Recipe::getLevel));
            for (Map.Entry<Integer, List<Recipe>> lvItems : facilityLvGroup.entrySet()) {
                for (Recipe recipe : lvItems.getValue()) {
                    Item output = recipe.getOutput();
                    JSONObject outItem = getItemInfo(output.getUid());
                    List<Price> sellPrices = JSON.parseArray(outItem.getString("sellPrices"), Price.class);
                    Price sellMaxPrice = sellPrices.stream().max(Comparator.comparing(Price::getPrice)).get();
                    BigDecimal totalCellPrice = sellMaxPrice.getPrice().multiply(BigDecimal.valueOf(output.amount));
                    Price price = new Price();
                    price.setPrice(totalCellPrice);
                    price.setType(sellMaxPrice.getType());
                    BigDecimal totalBuyPrice = BigDecimal.ZERO;
                    for (Item input : recipe.getInput()) {
                        JSONObject inItem = getItemInfo(input.getUid());
                        List<Price> buyPrices = JSON.parseArray(inItem.getString("buyPrices"), Price.class);
                        Price buyMinPrice = buyPrices.stream().min(Comparator.comparing(Price::getPrice)).get();
                        totalBuyPrice = totalBuyPrice.add(buyMinPrice.getPrice().multiply(BigDecimal.valueOf(input.amount)));
                    }
                    BigDecimal profit = totalCellPrice.subtract(totalBuyPrice);
                    Long craftTime = recipe.getCraftTime();
                    BigDecimal timeProfit=profit.divide(BigDecimal.valueOf(craftTime),2, RoundingMode.HALF_UP);
                    recipe.setTimeProfit(timeProfit);
                    recipe.setProfit(profit);
                }
            }
        }
    }


    private JSONObject getItemInfo(String uid) {
        JSONObject item = getItem(uid, uid);
        return getItem(item.getString("name"), uid);
    }

    private JSONObject getItem(String search, String uid) {
        HttpClientService httpClientService = HttpHelp.getInstance();
        String url = String.format(GET_ITEM_URL_FORMAT, new String(URLCodec.encodeUrl(null, search.getBytes())));
        HttpResult httpResult1 = httpClientService.get(url);
        JSONObject jsonObject = JSON.parseObject(httpResult1.getEntityStr());
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
