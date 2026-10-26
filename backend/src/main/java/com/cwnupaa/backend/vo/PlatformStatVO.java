package com.cwnupaa.backend.vo;

import java.io.Serializable;

public class PlatformStatVO implements Serializable {
    private String name;
    private Integer count;
    private String color;

    public PlatformStatVO() {
    }

    public PlatformStatVO(String name, Integer count, String color) {
        this.name = name;
        this.count = count;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}