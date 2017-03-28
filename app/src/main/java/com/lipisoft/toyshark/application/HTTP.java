package com.lipisoft.toyshark.application;

import java.util.List;

/**
 * Created by Lipi on 2017. 3. 28..
 */

public class HTTP {
    private List<String> httpHeaders;
    private String body;

    HTTP(List<String> httpHeaders, String body) {
        this.httpHeaders = httpHeaders;
        this.body = body;
    }

    public List<String> getHttpHeaders() {
        return httpHeaders;
    }

    public String getBody() {
        return body;
    }
}
