package com.cwnupaa.backend.vo;

import com.cwnupaa.backend.entity.ContestCategory;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class ContestCategoryVO extends ContestCategory {
    private String logoUrl;

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }
}