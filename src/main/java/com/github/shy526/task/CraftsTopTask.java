package com.github.shy526.task;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.shy526.MarkdownBuild;
import com.github.shy526.config.Config;
import com.github.shy526.config.Context;
import com.github.shy526.http.HttpClientService;
import com.github.shy526.http.HttpResult;
import com.github.shy526.service.GithubRestService;
import com.github.shy526.service.GithubRestServiceImpl;
import com.github.shy526.vo.Committer;
import com.github.shy526.vo.GithubVo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.net.URLCodec;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
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
                Iterator<Recipe> iterator = lvItems.getValue().iterator();
                while (iterator.hasNext()) {
                    Recipe recipe = iterator.next();
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
                        iterator.remove();
                        continue;
                    }
                    fullItemInfo(output,0);
                    for (Item input : recipe.getInput()) {
                        fullItemInfo(input,0);
                    }
                    Long craftTime = recipe.getCraftTime();
                    BigDecimal timeProfit = profit.divide(BigDecimal.valueOf(craftTime / 60f / 60), 2, RoundingMode.HALF_UP);
                    recipe.setTimeProfit(timeProfit);
                    recipe.setProfit(profit);
                    recipe.setSellPrice(totalSellPrice);
                    recipe.setBuyPrice(totalBuyPrice);
                }
                resultMap.put(item.getKey() + "-" + lvItems.getKey(), lvItems.getValue());
            }
        }


        MarkdownBuild markdownBuild = new MarkdownBuild();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("-yyyy-MM-dd HH:mm:ss");
        OkHttpClient okHttpClient = new OkHttpClient().newBuilder().build();
        CsrfToken csrfToken = new CsrfToken();
        for (Map.Entry<String, List<Recipe>> item : resultMap.entrySet()) {
            String key = item.getKey();
            markdownBuild.addTitle(key + simpleDateFormat.format(new Date()), 1);
            markdownBuild.addTableHeader("设施", "配方", "产出", "成本", "收益", "收益/h");
            for (Recipe recipe : item.getValue()) {
                StringBuilder outSb = getImgTextMarkdown(Collections.singletonList(recipe.getOutput()), markdownBuild, okHttpClient, csrfToken);
                StringBuilder inSb = getImgTextMarkdown(recipe.getInput(), markdownBuild, okHttpClient, csrfToken);
                markdownBuild.addTableBodyRow(recipe.getFacility(), inSb.toString(), outSb.toString(),
                        recipe.getSellPrice() + "₽", recipe.getProfit() + "₽", recipe.getTimeProfit() + "₽");
            }
        }
        GithubVo githubVo = new GithubVo();
        Config config = Context.getInstance(Config.class);
        githubVo.setRepo(config.getRepo());
        githubVo.setOwner(config.getOwner());
        githubVo.setMessage("update");
        githubVo.setContent(markdownBuild.build());
        Committer committer = new Committer();
        committer.setName("githubAction");
        committer.setEmail("githubAction@outloo.com");
        githubVo.setCommitter(committer);
        githubVo.setPath("README.md");
        JSONObject orUpdateFile = githubRestService.createOrUpdateFile(githubVo);
        log.info(orUpdateFile.toJSONString());
        log.info("{}->end->runTime{}ms", this.getClass().getSimpleName(), System.currentTimeMillis() - l);
    }

    private StringBuilder getImgTextMarkdown(List<Item> items, MarkdownBuild markdownBuild, OkHttpClient client, CsrfToken csrfToken) {
        StringBuilder result = new StringBuilder();
        for (Item item : items) {
            String cnName = item.getCnName();
            Integer amount = item.getAmount();
            Price totalPrice = item.getTotalPrice();
            if (item.getImg() == null) {
                continue;
            }
            String img = uploadImag(item.getImg(), csrfToken, client);

            System.out.println("totalPrice = " + csrfToken);
            result.append(markdownBuild.buildImgTextStyle(img, cnName, "X" + amount + "(" + totalPrice.getPrice() + "₽)"));
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

    private void fullItemInfo(Item temp, Integer skip) {
        JSONObject o = getItem(temp.getName()==null?temp.getShortName():temp.getName(), temp.getUid(), 3, skip);
        if (o.size()<=1&&!o.getBooleanValue("over")) {
            fullItemInfo(temp, skip + 20);
            return;
        }
        temp.setCnName(o.getString("cnName"));
        temp.setImg(o.getString("wikiIcon"));
    }


    private JSONObject getItemInfoBy(String search, String uid) {
        return getItem(search, uid, 6, 0);
    }

    private JSONObject getItem(String search, String uid, Integer count, Integer skip) {
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
                    TimeUnit.SECONDS.sleep(5);
                    result = getItem(search, uid, count, skip);

                } catch (Exception ignored) {
                }
            }
            JSONObject jsonObject = JSON.parseObject(httpResult.getEntityStr());
            JSONArray items = JSON.parseArray(deCodeString(jsonObject, "items"));
            if (items.size() <= 0) {
                result.put("over", true);
                return result;
            }else {
                result.put("over",false);
            }
            result = (JSONObject) items.stream().filter(item -> uid.equals(((JSONObject) item).getString("uid"))).findAny().get();
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

    private String uploadImag(String imgUrl, CsrfToken csrfToken, OkHttpClient client) {
        if (csrfToken.getCsrf() == null || csrfToken.getCsrfToken() == null) {
            System.out.println(csrfToken.toString());
            CsrfToken csrfToken1 = getCsrfToken();
            csrfToken.setCsrf(csrfToken1.getCsrf());
            csrfToken.setCsrfToken(csrfToken1.getCsrfToken());
        }
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
        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("uploads", "test.png",
                        RequestBody.create(MediaType.parse("application/octet-stream"), output.toByteArray()))
                .build();
        Request request = new Request.Builder()
                .url("https://picshack.net/upload")
                .method("POST", body)
                .addHeader("Accept", "application/json")
                .addHeader("X-Csrf-Token", csrfToken.getCsrf())
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("Content-Type", "multipart/form-data; boundary=----WebKitFormBoundaryR0HWSRbdM2lIae7x")
                .addHeader("Cookie", csrfToken.getCsrfToken()).build();
        try {
            Response response = client.newCall(request).execute();
            String str = response.body().string();
            System.out.println(str);
            str = JSONObject.parseObject(str).getJSONObject("data").getString("id");
            imgUrl = String.format("https://picshack.net/ib/%s.png", str);
            String header = response.header("set-cookie");
            StringBuilder sb = new StringBuilder();
            String[] split = header.split(";");
            for (String kv : split) {
                String[] kvArray = kv.split("=");
                if (kvArray[0].equals("_session")) {
                    sb.append(kv).append(";");
                } else if (kvArray[0].equals("XSRF-TOKEN")) {
                    sb.append(kv).append(";");
                }
            }
            if (sb.length() > 0) {
                System.out.println(sb.toString());
                csrfToken.setCsrfToken(sb.toString());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return imgUrl;
    }


    private CsrfToken getCsrfToken() {
        HttpClientService instance = Context.getInstance(HttpClientService.class);
        CsrfToken result = new CsrfToken();
        try (HttpResult httpResult = instance.get("https://picshack.net")) {
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
            String csrfToken = sb.substring(0, sb.length() - 1);
            result.setCsrf(csrf);
            result.setCsrfToken(csrfToken);
        } catch (Exception ignored) {
        }


        return result;
    }


    @Data
    public static class CsrfToken {
        private String csrf;
        private String csrfToken;
    }

    private void xxx(CsrfToken csrfToken) {
        csrfToken = new CsrfToken();
        System.out.println("csrfToken = " + csrfToken);
    }

    public static void main(String[] args) {


/*
        HttpClientService instance = Context.getInstance(HttpClientService.class);
        HttpResult httpResult = instance.get("https://picshack.net");
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
        String csrfToken = sb.substring(0, sb.length() - 1);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        // 设置为浏览器兼容模式（采用模拟浏览器提交的方式）
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
      //  builder.setContentType(ContentType.create("multipart/form-data"));
        File file = new File("/C:/Users/Administrator/Pictures/QQ截图20230929165346 - 副本.png");
        //FileBody fileBody = new FileBody(file);
       builder.addBinaryBody("uploads", file,ContentType.create("multipart/form-data"),file.getName());
       // builder.addPart("uploads", fileBody);
        RequestPack produce = RequestPack.produce("https://picshack.net/upload", null, HttpPost.class);
        HttpPost postRequest = (HttpPost) produce.getRequestBase();
        postRequest.setEntity(builder.build());
        postRequest.addHeader("Accept", "application/json");
        postRequest.addHeader("X-Csrf-Token", csrf);
        postRequest.addHeader("X-Requested-With", "XMLHttpRequest");
        postRequest.addHeader("Cache-Control", "no-cache");
        postRequest.addHeader("Content-Type", "multipart/form-data; boundary=----WebKitFormBoundaryR0HWSRbdM2lIae7x");
        postRequest.addHeader("Cookie", csrfToken);
        HttpResult execute = instance.execute(postRequest);
        System.out.println("execute = " + execute.getEntityStr());
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            CloseableHttpResponse execute1 = httpClient.execute(postRequest);
            System.out.println(EntityUtils.toString(execute1.getEntity()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
*/


    }
}
