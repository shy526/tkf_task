package com.github.shy526;

import javafx.util.Builder;

import java.io.File;

public class MarkdownBuild {
    private final StringBuilder markdown = new StringBuilder();
    private static final String TABLE_SEPARATOR = "|";
    private static final String TABLE_ALIGN_CENTER = ":---:";
    private static final String ENTER = "\n";
    private static final String TITLE_SEPARATOR = "#";


    private static final String IMG_TEXT_FORMAT = "<div align=\"center\"><img src=\"%s\" alt=\"%s\"/><br/><font>%s</font></div>";

    private static final String CENTER_TEXT_FORMAT = "<center>%s</center> ";
    private static final String IMG_FORMAT = "![%s](%s) ";

    public MarkdownBuild addEnter() {
        markdown.append(ENTER);
        return this;
    }

    public StringBuilder buildImg(String alt, String src) {
        return new StringBuilder(String.format(IMG_FORMAT, alt, src));
    }

    public StringBuilder buildCenterTextStyle(String str) {
        return new StringBuilder(String.format(CENTER_TEXT_FORMAT, str));
    }

    public StringBuilder buildImgTextStyle(String src, String alt, String text) {
        return new StringBuilder(String.format(IMG_TEXT_FORMAT, src, alt, text));
    }

    public MarkdownBuild addTableHeader(String... names) {
        StringBuilder tableHeader = new StringBuilder(TABLE_SEPARATOR);
        StringBuilder tableAlign = new StringBuilder(TABLE_SEPARATOR);
        for (String name : names) {
            tableHeader.append(name).append(TABLE_SEPARATOR);
            tableAlign.append(TABLE_ALIGN_CENTER).append(TABLE_SEPARATOR);
        }
        tableHeader.append(ENTER).append(tableAlign);
        markdown.append(tableHeader).append(ENTER);
        return this;
    }


    public MarkdownBuild addTableBodyRow(String... strArray) {
        StringBuilder bodyRow = new StringBuilder(TABLE_SEPARATOR);
        for (String item : strArray) {
            bodyRow.append(item).append(TABLE_SEPARATOR);
        }
        markdown.append(bodyRow).append(ENTER);
        return this;
    }

    public MarkdownBuild addTitle(String title, Integer lv) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lv; i++) {
            result.append(TITLE_SEPARATOR);
        }
        result.append(" ").append(title);
        markdown.append(result).append(ENTER);
        return this;
    }

    public String build() {
        return markdown.toString();
    }

    public static void main(String[] args) {
        MarkdownBuild markdownBuild = new MarkdownBuild();
        markdownBuild.addTitle("逃离塔科夫藏身处收益", 1);
        markdownBuild.addTableHeader("设施", "配方", "产出", "成本", "收益", "收益/h");
        markdownBuild.addTableBodyRow("医疗", "11+1", "xxx", "111", "22", "444");
        String build = markdownBuild.build();
        System.out.println(build);


    }
}
