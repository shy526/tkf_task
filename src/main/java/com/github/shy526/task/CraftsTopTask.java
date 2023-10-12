package com.github.shy526.task;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.github.shy526.MarkdownBuild;
import com.github.shy526.config.Config;
import com.github.shy526.config.Context;
import com.github.shy526.http.HttpClientService;
import com.github.shy526.http.HttpResult;
import com.github.shy526.http.RequestPack;
import com.github.shy526.service.GithubRestService;
import com.github.shy526.service.GithubRestServiceImpl;
import com.github.shy526.vo.Committer;
import com.github.shy526.vo.GithubVo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.net.URLCodec;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
public class CraftsTopTask implements Task {
    private final static String GET_ITEM_URL_FORMAT = "https://tarkov-market.com/api/be/items?lang=cn&search=%s&tag=&sort=change24&sort_direction=desc&trader=&skip=%s";
    private final static String GET_RECIPES_URL_FORMAT = "https://tarkov-market.com/api/be/hideout";
    HttpClientService httpClientService = Context.getInstance(HttpClientService.class);
    GithubRestService githubRestService = Context.getInstance(GithubRestServiceImpl.class);
    private static final JSONObject ITEM_CACHE = new JSONObject();
    private static final JSONObject IMG_CACHE = new JSONObject();


    @Override
    public void run() {
        long l = System.currentTimeMillis();
        GithubVo githubVo3 = buildGithubVo();
        githubVo3.setPath("imgMap.txt");
        JSONObject content = githubRestService.getContent(githubVo3);
        if (content != null) {
            String str = content.getString("content");
            str = new String(Base64.decodeBase64(str));
            JSONObject temp = JSONObject.parseObject(str);
            if (!temp.isEmpty()) {
                log.info("imag cache load : {}", temp.size());
                IMG_CACHE.putAll(temp);
            }
        }
        JSONObject result = httpGetJsonObject(GET_RECIPES_URL_FORMAT);
        String recipesStr = deCodeString(result, "recipes");
        List<Recipe> recipes = JSON.parseArray(recipesStr, Recipe.class);
        //按设施分组
        Map<String, List<Recipe>> facilityGroup = recipes.stream().collect(Collectors.groupingBy(Recipe::getFacility));
        List<Recipe> recipeResult = new ArrayList<Recipe>();
        for (Map.Entry<String, List<Recipe>> facilityEntry : facilityGroup.entrySet()) {
            List<Recipe> facilityRecipe = facilityEntry.getValue();
            ListIterator<Recipe> recipeListIterator = facilityRecipe.listIterator();
            while (recipeListIterator.hasNext()) {
                Recipe recipe = recipeListIterator.next();
                //region 产出获取
                Item output = recipe.getOutput();
                JSONObject outItem = getItemInfoBy(output.getUid(), output.getUid());
                output.setName(outItem.getString("name"));
                List<Price> sellPrices = JSON.parseArray(outItem.getString("sellPrices"), Price.class);
                Price sellMaxPrice = sellPrices.stream().filter(item -> {
                    String type = item.getType();
                    return !type.equals("trader") && !type.equals("historical");
                }).max(Comparator.comparing(Price::getPrice)).get();
                BigDecimal totalSellPrice = sellMaxPrice.getPrice().multiply(output.getAmount()).setScale(2, RoundingMode.HALF_UP);
                Price price = new Price();
                price.setPrice(totalSellPrice);
                price.setType(sellMaxPrice.getType());
                price.setTrader(sellMaxPrice.getTrader());
                output.setTotalPrice(price);
                //endregion
                BigDecimal totalBuyPrice = BigDecimal.ZERO;
                //配方
                for (Item input : recipe.getInput()) {
                    JSONObject inItem = getItemInfoBy(input.getUid(), input.getUid());
                    input.setName(inItem.getString("name"));
                    List<Price> buyPrices = JSON.parseArray(inItem.getString("buyPrices"), Price.class);
                    Price buyMinPrice = buyPrices.stream().filter(item -> !item.getType().equals("craft"))
                            .min(Comparator.comparing(Price::getPrice)).orElseGet(() -> buyPrices.get(0));
                    BigDecimal temp = buyMinPrice.getPrice().multiply(input.getAmount()).setScale(2, RoundingMode.HALF_UP);
                    totalBuyPrice = totalBuyPrice.add(temp);
                    buyMinPrice.setPrice(temp);
                    input.setTotalPrice(buyMinPrice);
                }
                BigDecimal profit = totalSellPrice.subtract(totalBuyPrice);
                if (BigDecimal.ZERO.compareTo(profit) >= 0) {
                    recipeListIterator.remove();
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
            facilityRecipe.sort((o1, o2) -> o2.getProfit().compareTo(o1.getProfit()));
            recipeResult.addAll(facilityRecipe);
        }
        MarkdownBuild markdownBuild = new MarkdownBuild();
        String facilityTemp = null;
        LocalDateTime now = LocalDateTime.now();
        ZonedDateTime convertedTime = ZonedDateTime.of(now, ZoneId.of("Asia/Shanghai"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String formatTime = convertedTime.format(formatter);
        markdownBuild.addTitle("塔科夫藏身处收益(不含工具,每日更新)[" + formatTime + "]", 1);
        markdownBuild.addEnter().addEnter().addEnter();
        for (Recipe recipe : recipeResult) {
            String facility = recipe.getFacility();
            Integer lv = recipe.getLevel();
            if (!facility.equals(facilityTemp)) {
                StringBuilder sb = markdownBuild.buildImg(facility, getFacilityImg(recipe));
                StringBuilder facilitySb = markdownBuild.buildCenterTextStyle(sb.toString());
                markdownBuild.addTitle(facilitySb.toString(), 2).addEnter().addEnter().addEnter();
                markdownBuild.addTableHeader("lv", "配方", "产出", "成本", "收益", "收益/h");
                facilityTemp = facility;
            }
            StringBuilder outSb = getImgTextMarkdown(Collections.singletonList(recipe.getOutput()), markdownBuild);
            StringBuilder inSb = getImgTextMarkdown(recipe.getInput(), markdownBuild);
            markdownBuild.addTableBodyRow(lv.toString(), inSb.toString(), outSb.toString(),
                    recipe.getBuyPrice() + "₽", recipe.getProfit() + "₽", recipe.getTimeProfit() + "₽");
        }
        GithubVo githubVo = buildGithubVo();
        githubVo.setContent(markdownBuild.build());
        githubVo.setPath("README.md");
        JSONObject orUpdateFile = githubRestService.createOrUpdateFile(githubVo);


        GithubVo githubVo2 = buildGithubVo();
        githubVo2.setContent(JSON.toJSONString(IMG_CACHE, SerializerFeature.PrettyFormat));
        githubVo2.setPath("imgMap.txt");
        JSONObject orUpdateFile2 = githubRestService.createOrUpdateFile(githubVo2);
        log.info("{}->end->runTime{}ms", this.getClass().getSimpleName(), System.currentTimeMillis() - l);
    }

    private GithubVo buildGithubVo() {
        GithubVo githubVo = new GithubVo();
        Config config = Context.getInstance(Config.class);
        githubVo.setRepo(config.getRepo());
        githubVo.setOwner(config.getOwner());
        githubVo.setMessage("update");
        Committer committer = new Committer();
        committer.setName("githubAction");
        committer.setEmail("githubAction@outloo.com");
        githubVo.setCommitter(committer);
        return githubVo;
    }

    /**
     * 获取藏身处图片
     *
     * @param recipe recipe
     * @return String
     */
    private String getFacilityImg(Recipe recipe) {
        return uploadImag(String.format("https://tarkov.dev/images/stations/%s-icon.png", recipe.getFacility().toLowerCase().replaceAll("\\s{1}", "-")));
    }

    private StringBuilder getImgTextMarkdown(List<Item> items, MarkdownBuild markdownBuild) {
        StringBuilder result = new StringBuilder();
        for (Item item : items) {
            String uid = item.getUid();
            BigDecimal amount = item.getAmount();
            Price totalPrice = item.getTotalPrice();
            String img = uploadImag(item.getImg());
            String name = totalPrice.getTrader();
            String type = totalPrice.getType();
            name = type.equals("flea") ? "跳蚤" : name;
            name = type.equals("craft") ? "藏身处" : name;
            name = type.equals("barter") ? "交换."+name : name;
            name = name == null ? type : name;
            result.append(markdownBuild.buildImgTextStyle(img, uid, "X" + amount + "(" + name + " : " + totalPrice.getPrice() + "₽)"));
        }
        return result;
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
        JSONObject o = getItem(temp.getName() == null ? temp.getShortName() : temp.getName(), temp.getUid(), 6, 0);
        temp.setCnName(o.getString("cnName"));
        temp.setImg(o.getString("wikiIcon"));
    }


    private JSONObject getItemInfoBy(String search, String uid) {
        return getItem(search, uid, 6, 0);
    }


    private JSONObject getItem(String search, String uid, Integer count, Integer skip) {
        String key = search + "-" + uid;
        if (ITEM_CACHE.containsKey(key)) {
            log.error("get item cache :{}", key);
            return ITEM_CACHE.getJSONObject(key);
        }
        String url = String.format(GET_ITEM_URL_FORMAT, new String(URLCodec.encodeUrl(null, search.getBytes())), skip.toString());
        JSONObject result = new JSONObject();
        try (HttpResult httpResult = httpClientService.get(url)) {
            Integer httpStatus = httpResult.getHttpStatus();
            if (httpStatus.equals(429)) {
                try {
                    count--;
                    if (count <= 0) {
                        return null;
                    }
                    TimeUnit.SECONDS.sleep(10);
                    result = getItem(search, uid, count, skip);

                } catch (Exception ignored) {
                }
            }
            JSONObject jsonObject = JSON.parseObject(httpResult.getEntityStr());
            JSONArray items = JSON.parseArray(deCodeString(jsonObject, "items"));
            if (items.isEmpty()) {
                log.error("{}---> not find item", uid);
                ITEM_CACHE.put(key, result);
                return result;
            }
            Optional<Object> op = items.stream().filter(item -> uid.equals(((JSONObject) item).getString("uid"))).findAny();
            if (!op.isPresent()) {
                result = getItem(search, uid, count, skip + 20);
            } else {
                result = (JSONObject) op.get();
            }
        } catch (Exception ignored) {
        }
        ITEM_CACHE.put(key, result);
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
        private BigDecimal amount;
        private String name;
        private String cnName;
        private Price totalPrice;
        private String img;
    }

    @Data
    public static class Price {
        private String type;
        private BigDecimal price;

        private String trader;
    }


    private String uploadImag(String imgUrl) {
        String key = imgUrl.split("\\?")[0];
        key = DigestUtils.md5Hex(key);
        if (IMG_CACHE.containsKey(key)) {
            log.info("get img cache : {}", imgUrl);
            return IMG_CACHE.getString(key);
        }
        getCsrfToken();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (HttpResult httpResult = httpClientService.get(imgUrl);
             InputStream content = new BufferedInputStream(httpResult.getResponse().getEntity().getContent());
        ) {
            byte[] buffer = new byte[1024 * 4];
            int n = 0;
            while (-1 != (n = content.read(buffer))) {
                output.write(buffer, 0, n);
            }
        } catch (Exception ignored) {
        }
        byte[] byteArray = output.toByteArray();
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addBinaryBody("uploads", byteArray, ContentType.MULTIPART_FORM_DATA, "test.png");


        RequestPack produce = RequestPack.produce("https://picshack.net/upload", null, HttpPost.class);
        HttpPost postRequest = (HttpPost) produce.getRequestBase();
        HttpEntity build = builder.build();

        postRequest.setEntity(build);
        postRequest.addHeader("Accept", "application/json");
        postRequest.addHeader("X-Csrf-Token", System.getProperty("csrf"));
        postRequest.addHeader("X-Requested-With", "XMLHttpRequest");
        postRequest.addHeader("Cache-Control", "no-cache");
        postRequest.addHeader("Cookie", System.getProperty("csrfToken"));

        try (HttpResult execute = httpClientService.execute(produce)) {
            if (execute.getHttpStatus().equals(200)) {
                imgUrl = String.format("https://picshack.net/ib/%s.png", JSONObject.parseObject(execute.getEntityStr()).getJSONObject("data").getString("id"));
                IMG_CACHE.put(key, imgUrl);
            }

        } catch (Exception ignored) {
        }
        return imgUrl;
    }


    private void getCsrfToken() {
        if (System.getProperty("csrf") != null && System.getProperty("csrfToken") != null) {
            return;
        }

        try (HttpResult httpResult = httpClientService.get("https://picshack.net")) {
            CloseableHttpResponse cre = httpResult.getResponse();
            Header[] headers = cre.getHeaders("Set-Cookie");
            StringBuilder sb = new StringBuilder();
            for (Header header : headers) {
                for (String item : header.getValue().split(";")) {
                    String[] split = item.split("=");
                    if (split[0].equals("_session")) {
                        sb.append(item).append(";");
                    } else if (split[0].equals("XSRF-TOKEN")) {
                        sb.append(item).append(";");
                    }
                }
            }
            Document doc = Jsoup.parse(httpResult.getEntityStr());
            Elements select = doc.select("meta[name=csrf-token]");
            String csrf = select.attr("content");
            String csrfToken = sb.toString();
            System.setProperty("csrf", csrf);
            System.setProperty("csrfToken", csrfToken);
        } catch (Exception ignored) {
        }
    }


}
